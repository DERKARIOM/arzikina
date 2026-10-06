package com.naniger.arzikina.presentation.utilities.loans

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.LoanPayment
import com.naniger.arzikina.domain.model.LoanType
import com.naniger.arzikina.domain.repository.AccountRepository
import com.naniger.arzikina.domain.repository.LoanRepository
import com.naniger.arzikina.domain.repository.PersonRepository
import com.naniger.arzikina.domain.repository.TransactionRepository
import com.naniger.arzikina.presentation.accounts.computeCurrentBalances
import com.naniger.arzikina.util.Constants
import com.naniger.arzikina.util.LoanDateTime
import com.naniger.arzikina.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
 * même principe que [com.naniger.arzikina.presentation.accounts.AccountFormViewModel] en mode édition)
 * n'est pas encore disponible — [LoanPaymentFormFragment] garde le bouton d'enregistrement désactivé
 * jusque-là plutôt que d'afficher des valeurs par défaut trompeuses (restant à 0, etc.).
 *
 * [accountId] est pré-rempli avec [com.naniger.arzikina.domain.model.Loan.accountId] (compte utilisé à
 * la création du prêt/emprunt) mais reste modifiable : un remboursement peut être réglé sur un
 * compte différent (cahier des charges section 11, voir la doc de [LoanPayment.accountId]).
 *
 * [amountInput] est saisi dans la devise DU PRÊT ([loanCurrencyCode], celle de son compte
 * d'origine) — pas celle du compte de règlement choisi, qui peut différer : Arzikina ne fait
 * aucune conversion de change nulle part (voir la doc de [com.naniger.arzikina.domain.model.Loan]), un
 * remboursement partagé entre deux devises resterait donc incohérent quel que soit l'écran ; ce
 * choix garde au moins la cohérence avec [loanRemainingAmount]/la barre de progression affichées
 * sur "Détail du prêt".
 *
 * Mode édition ([isEditMode], argument `paymentId` non nul) : formulaire pré-rempli avec le
 * remboursement existant ; le montant autorisé va jusqu'au solde restant PLUS le montant actuel de
 * ce remboursement ([editedPaymentAmount]), puisque celui-ci sera remplacé.
 */
data class LoanPaymentFormState(
    val isLoaded: Boolean = false,
    /** `true` si [loanId] ne correspond à aucun prêt/emprunt existant (ex. supprimé depuis un
     * autre écran pendant que ce formulaire restait ouvert, voir [LoanPaymentFormViewModel.init]) —
     * sans ce champ, le bouton d'enregistrement resterait désactivé indéfiniment (`isLoaded` ne
     * devenant jamais `true`) sans aucune explication pour l'utilisateur. */
    val notFound: Boolean = false,
    val loanTitle: String = "",
    val personName: String = "",
    val loanType: LoanType = LoanType.LENT,
    val loanRemainingAmount: Long = 0L,
    val loanCurrencyCode: String = Constants.DEFAULT_CURRENCY_CODE,
    /** Date de début du prêt/emprunt (`Loan.startDate`) : un remboursement ne peut pas la précéder
     *  (comparaison par JOUR, voir [LoanPaymentFormViewModel.save]). 0 tant que non chargé. */
    val loanStartDate: Long = 0L,
    val accountId: Long = 0L,
    val amountInput: String = "",
    /** Date ET heure du remboursement, un seul instant (voir `LoanDateTime`, mêmes règles que la
     * date du prêt). Par défaut : maintenant, à la minute. */
    val dateMillis: Long = LoanDateTime.nowToMinute(),
    val note: String = "",
    @StringRes val accountError: Int? = null,
    @StringRes val amountError: Int? = null,
    @StringRes val dateError: Int? = null,
    val isSaving: Boolean = false,
    val isEditMode: Boolean = false,
    /** Montant actuel du remboursement modifié (0 en création). */
    val editedPaymentAmount: Long = 0L,
    /** Date d'origine du remboursement modifié (0 en création) — voir [LoanPaymentFormViewModel.save]. */
    val originalPaymentDate: Long = 0L
) {
    /** Montant maximal accepté : le solde restant, plus le remboursement remplacé en édition. */
    val maxAmount: Long get() = loanRemainingAmount + editedPaymentAmount
}

sealed interface LoanPaymentFormEvent {
    data object Saved : LoanPaymentFormEvent
    data object Updated : LoanPaymentFormEvent
    data object Deleted : LoanPaymentFormEvent
}

@HiltViewModel
class LoanPaymentFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loanRepository: LoanRepository,
    private val personRepository: PersonRepository,
    private val accountRepository: AccountRepository,
    transactionRepository: TransactionRepository
) : ViewModel() {

    private val args = LoanPaymentFormFragmentArgs.fromSavedStateHandle(savedStateHandle)
    private val loanId: Long = args.loanId

    /** 0 en création ; id du remboursement modifié sinon. */
    private val paymentId: Long = args.paymentId
    val isEditMode: Boolean get() = paymentId != 0L

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
            val loan = loanRepository.getLoan(loanId)
            if (loan == null) {
                _formState.update { it.copy(notFound = true) }
                return@launch
            }
            // Édition : le remboursement doit toujours exister (supprimé ailleurs → même retour
            // arrière que pour un prêt introuvable).
            val payment = if (isEditMode) {
                loanRepository.observePayments(loanId).first().firstOrNull { it.id == paymentId }
                    ?: run {
                        _formState.update { it.copy(notFound = true) }
                        return@launch
                    }
            } else {
                null
            }
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
                    loanStartDate = loan.startDate,
                    accountId = payment?.accountId ?: loan.accountId,
                    amountInput = payment?.let { p -> Money.formatForInput(p.amount) } ?: it.amountInput,
                    dateMillis = payment?.date ?: it.dateMillis,
                    note = payment?.note ?: it.note,
                    isEditMode = payment != null,
                    editedPaymentAmount = payment?.amount ?: 0L,
                    originalPaymentDate = payment?.date ?: 0L
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

    /** [millis] : jour choisi (minuit local, voir `LoanPaymentFormFragment.showDatePicker`) —
     * l'heure déjà choisie est CONSERVÉE (voir [onTimeChange]). */
    fun onDateChange(millis: Long) {
        _formState.update {
            it.copy(dateMillis = LoanDateTime.withDate(it.dateMillis, LoanDateTime.toLocalDate(millis)), dateError = null)
        }
    }

    fun onTimeChange(hour: Int, minute: Int) {
        _formState.update { it.copy(dateMillis = LoanDateTime.withTime(it.dateMillis, hour, minute), dateError = null) }
    }

    fun onNoteChange(value: String) {
        _formState.update { it.copy(note = value) }
    }

    fun save() {
        val state = _formState.value
        // Garde anti double-soumission : voir `LoanFormViewModel.save` pour le même raisonnement.
        if (state.isSaving) return
        val accountError = if (state.accountId == 0L) R.string.error_select_account else null
        val amountMinor = Money.parseToMinorUnits(state.amountInput)
        val amountError = when {
            amountMinor == null || amountMinor <= 0L -> R.string.error_invalid_amount
            amountMinor > state.maxAmount -> R.string.error_amount_exceeds_remaining
            else -> null
        }
        // Un remboursement ne peut pas précéder le prêt/emprunt (même règle que l'application Web,
        // et symétrique de `loan_detail_start_after_payment_error`). Comparaison par JOUR : le même
        // jour reste accepté quelle que soit l'heure. En modification, un ancien remboursement déjà
        // antérieur dont la date n'est pas touchée reste enregistrable (correction de la note, du
        // montant…) : la règle ne bloque que les dates nouvellement saisies.
        val dateChanged = !state.isEditMode || state.dateMillis != state.originalPaymentDate
        val dateError = if (dateChanged && LoanDateTime.isBeforeDay(state.dateMillis, state.loanStartDate)) {
            R.string.loan_payment_form_date_before_loan_error
        } else {
            null
        }

        if (accountError != null || amountError != null || dateError != null) {
            _formState.update { it.copy(accountError = accountError, amountError = amountError, dateError = dateError) }
            return
        }
        checkNotNull(amountMinor)

        _formState.update { it.copy(isSaving = true) }
        if (isEditMode) {
            viewModelScope.launch {
                loanRepository.updatePayment(
                    LoanPayment(
                        id = paymentId,
                        loanId = loanId,
                        accountId = state.accountId,
                        amount = amountMinor,
                        date = state.dateMillis,
                        note = state.note.trim(),
                        // Ignorés par LoanRepository.updatePayment (jamais modifiés).
                        transactionId = 0L,
                        createdAt = 0L
                    )
                )
                _formState.update { it.copy(isSaving = false) }
                _events.emit(LoanPaymentFormEvent.Updated)
            }
            return
        }
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

    /** Suppression depuis le formulaire en édition (confirmée par le Fragment) : supprime aussi
     * la transaction liée et recalcule le prêt/emprunt (voir [LoanRepository.deletePayment]). */
    fun delete() {
        if (!isEditMode || _formState.value.isSaving) return
        _formState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            loanRepository.deletePayment(paymentId)
            _formState.update { it.copy(isSaving = false) }
            _events.emit(LoanPaymentFormEvent.Deleted)
        }
    }
}
