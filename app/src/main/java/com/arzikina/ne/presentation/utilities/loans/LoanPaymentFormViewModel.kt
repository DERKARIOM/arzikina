package com.arzikina.ne.presentation.utilities.loans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.LoanPayment
import com.arzikina.ne.domain.model.LoanType
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.LoanRepository
import com.arzikina.ne.domain.repository.PersonRepository
import com.arzikina.ne.domain.repository.TransactionRepository
import com.arzikina.ne.presentation.accounts.computeCurrentBalances
import com.arzikina.ne.util.Constants
import com.arzikina.ne.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * État du formulaire "Enregistrer un remboursement" — un seul écran (contrairement au formulaire
 * d'ajout d'un prêt/emprunt en 2 pages, voir [LoanFormViewModel]) : moins de champs, pas besoin de
 * page de résumé séparée.
 *
 * [isLoaded] : `false` tant que le prêt/emprunt (chargé une seule fois, voir [LoanPaymentFormViewModel.init],
 * même principe que [com.arzikina.ne.presentation.accounts.AccountFormViewModel] en mode édition)
 * n'est pas encore disponible — [LoanPaymentFormFragment] garde le bouton d'enregistrement désactivé
 * jusque-là plutôt que d'afficher des valeurs par défaut trompeuses (restant à 0, etc.).
 *
 * [accountId] est pré-rempli avec [com.arzikina.ne.domain.model.Loan.accountId] (compte utilisé à
 * la création du prêt/emprunt) mais reste modifiable : un remboursement peut être réglé sur un
 * compte différent (cahier des charges section 11, voir la doc de [LoanPayment.accountId]).
 *
 * [amountInput] est saisi dans la devise DU PRÊT ([loanCurrencyCode], celle de son compte
 * d'origine) — pas celle du compte de règlement choisi, qui peut différer : Arzikina ne fait
 * aucune conversion de change nulle part (voir la doc de [com.arzikina.ne.domain.model.Loan]), un
 * remboursement partagé entre deux devises resterait donc incohérent quel que soit l'écran ; ce
 * choix garde au moins la cohérence avec [loanRemainingAmount]/la barre de progression affichées
 * sur "Détail du prêt".
 */
data class LoanPaymentFormState(
    val isLoaded: Boolean = false,
    val loanTitle: String = "",
    val personName: String = "",
    val loanType: LoanType = LoanType.LENT,
    val loanRemainingAmount: Long = 0L,
    val loanCurrencyCode: String = Constants.DEFAULT_CURRENCY_CODE,
    val accountId: Long = 0L,
    val amountInput: String = "",
    val dateMillis: Long = System.currentTimeMillis(),
    val note: String = "",
    val accountError: String? = null,
    val amountError: String? = null,
    val isSaving: Boolean = false
)

sealed interface LoanPaymentFormEvent {
    data object Saved : LoanPaymentFormEvent
}

@HiltViewModel
class LoanPaymentFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loanRepository: LoanRepository,
    private val personRepository: PersonRepository,
    private val accountRepository: AccountRepository,
    transactionRepository: TransactionRepository
) : ViewModel() {

    private val loanId: Long = savedStateHandle.get<Long>(LOAN_ID_ARG) ?: 0L

    private val _formState = MutableStateFlow(LoanPaymentFormState())
    val formState: StateFlow<LoanPaymentFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<LoanPaymentFormEvent>()
    val events: SharedFlow<LoanPaymentFormEvent> = _events.asSharedFlow()

    /** Voir `LoanFormViewModel.accounts` pour le même raisonnement. */
    val accounts: StateFlow<List<Account>> = accountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyList())

    val accountBalances: StateFlow<Map<Long, Long>> = combine(
        accounts,
        transactionRepository.observeTransactions()
    ) { accounts, transactions -> computeCurrentBalances(accounts, transactions) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyMap())

    init {
        viewModelScope.launch {
            val loan = loanRepository.getLoan(loanId) ?: return@launch
            val person = personRepository.getPerson(loan.personId)
            val account = accountRepository.getAccount(loan.accountId)
            _formState.update {
                it.copy(
                    isLoaded = true,
                    loanTitle = loan.description,
                    personName = person?.name.orEmpty(),
                    loanType = loan.type,
                    loanRemainingAmount = loan.remainingAmount,
                    loanCurrencyCode = account?.currencyCode ?: Constants.DEFAULT_CURRENCY_CODE,
                    accountId = loan.accountId
                )
            }
        }
    }

    fun onAccountSelected(account: Account) {
        _formState.update { it.copy(accountId = account.id, accountError = null) }
    }

    fun onAmountChange(value: String) {
        _formState.update { it.copy(amountInput = value, amountError = null) }
    }

    fun onDateChange(millis: Long) {
        _formState.update { it.copy(dateMillis = millis) }
    }

    fun onNoteChange(value: String) {
        _formState.update { it.copy(note = value) }
    }

    fun save() {
        val state = _formState.value
        val accountError = if (state.accountId == 0L) "Choisis un compte" else null
        val amountMinor = Money.parseToMinorUnits(state.amountInput)
        val amountError = when {
            amountMinor == null || amountMinor <= 0L -> "Montant invalide"
            amountMinor > state.loanRemainingAmount -> "Ne peut pas dépasser le solde restant"
            else -> null
        }

        if (accountError != null || amountError != null) {
            _formState.update { it.copy(accountError = accountError, amountError = amountError) }
            return
        }
        checkNotNull(amountMinor)

        _formState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            loanRepository.recordPayment(
                LoanPayment(
                    loanId = loanId,
                    accountId = state.accountId,
                    amount = amountMinor,
                    date = state.dateMillis,
                    note = state.note.trim(),
                    // Recalculé par LoanRepositoryImpl.recordPayment : valeur ici sans importance.
                    transactionId = 0L,
                    createdAt = System.currentTimeMillis()
                )
            )
            _formState.update { it.copy(isSaving = false) }
            _events.emit(LoanPaymentFormEvent.Saved)
        }
    }

    private companion object {
        const val LOAN_ID_ARG = "loanId"
    }
}
