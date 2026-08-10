package com.arzikina.ne.presentation.utilities.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Loan
import com.arzikina.ne.domain.model.LoanPayment
import com.arzikina.ne.domain.model.LoanReason
import com.arzikina.ne.domain.model.LoanStatus
import com.arzikina.ne.domain.model.LoanType
import com.arzikina.ne.domain.model.Person
import com.arzikina.ne.domain.model.RepaymentMode
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.LoanRepository
import com.arzikina.ne.domain.repository.PersonRepository
import com.arzikina.ne.domain.repository.TransactionRepository
import com.arzikina.ne.presentation.accounts.computeCurrentBalances
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * État du formulaire d'ajout d'un prêt/emprunt, en 2 pages (voir maquette "Ajouter un prêt /
 * emprunt" 1/2 puis "Résumé du prêt / emprunt") — un seul écran/[ViewModel], pas deux
 * destinations de navigation séparées : [step] détermine simplement quelle page est visible
 * (voir [LoanFormFragment]), ce qui évite de faire transiter tout l'état saisi via des arguments
 * de navigation entre deux Fragments.
 *
 * [reason]/[repaymentMode] (voir [Loan]) : AUCUN champ dédié sur la maquette fournie pour ces
 * deux propriétés — valeurs par défaut [LoanReason.OTHER]/[RepaymentMode.SINGLE] appliquées à la
 * création (voir [save]). Un sélecteur dédié pourra être ajouté plus tard sans migration (simple
 * changement de valeur d'enum, voir la doc de [com.arzikina.ne.data.local.database.Converters]).
 *
 * Pas de mode édition ici (contrairement à [com.arzikina.ne.presentation.accounts.AccountFormViewModel]) :
 * cette Étape couvre uniquement la CRÉATION (voir le plan de développement Prêts/Emprunts,
 * "Ajout d'un prêt/emprunt" — l'édition n'est pas demandée à ce stade).
 */
data class LoanFormState(
    val step: Int = 1,
    val type: LoanType = LoanType.LENT,
    val personId: Long = 0L,
    val personName: String = "",
    val accountId: Long = 0L,
    val amountInput: String = "",
    val startDateMillis: Long = System.currentTimeMillis(),
    val dueDateMillis: Long = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30),
    val description: String = "",
    val firstPaymentAmountInput: String = "",
    val firstPaymentDateMillis: Long = System.currentTimeMillis(),
    val personError: String? = null,
    val accountError: String? = null,
    val amountError: String? = null,
    val dueDateError: String? = null,
    val firstPaymentAmountError: String? = null,
    val isSaving: Boolean = false
)

sealed interface LoanFormEvent {
    data object Saved : LoanFormEvent
}

@HiltViewModel
class LoanFormViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    private val personRepository: PersonRepository,
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(LoanFormState())
    val formState: StateFlow<LoanFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<LoanFormEvent>()
    val events: SharedFlow<LoanFormEvent> = _events.asSharedFlow()

    /** Chargés une fois : voir `TransactionFormViewModel.accounts` pour le même raisonnement. */
    val accounts: StateFlow<List<Account>> = accountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyList())

    val persons: StateFlow<List<Person>> = personRepository.observePersons()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyList())

    /** Solde COURANT de chaque compte (voir [com.arzikina.ne.presentation.components.AccountPickerDialog]) —
     * même raisonnement que `TransactionFormViewModel.accountBalances`. */
    val accountBalances: StateFlow<Map<Long, Long>> = combine(
        accounts,
        transactionRepository.observeTransactions()
    ) { accounts, transactions -> computeCurrentBalances(accounts, transactions) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyMap())

    fun onTypeChange(type: LoanType) {
        _formState.update { it.copy(type = type) }
    }

    fun onPersonSelected(person: Person) {
        _formState.update { it.copy(personId = person.id, personName = person.name, personError = null) }
    }

    /** Voir [PersonPickerDialog] : création à la volée d'une personne absente de la liste. */
    fun onNewPersonCreated(name: String, phone: String?) {
        viewModelScope.launch {
            val newPersonId = personRepository.savePerson(
                Person(name = name, phone = phone?.takeIf { it.isNotBlank() }, createdAt = System.currentTimeMillis())
            )
            _formState.update { it.copy(personId = newPersonId, personName = name, personError = null) }
        }
    }

    fun onAccountSelected(account: Account) {
        _formState.update { it.copy(accountId = account.id, accountError = null) }
    }

    fun onAmountChange(value: String) {
        _formState.update { it.copy(amountInput = value, amountError = null) }
    }

    fun onStartDateChange(millis: Long) {
        _formState.update { it.copy(startDateMillis = millis, dueDateError = null) }
    }

    fun onDueDateChange(millis: Long) {
        _formState.update { it.copy(dueDateMillis = millis, dueDateError = null) }
    }

    fun onDescriptionChange(value: String) {
        _formState.update { it.copy(description = value) }
    }

    fun onFirstPaymentAmountChange(value: String) {
        _formState.update { it.copy(firstPaymentAmountInput = value, firstPaymentAmountError = null) }
    }

    fun onFirstPaymentDateChange(millis: Long) {
        _formState.update { it.copy(firstPaymentDateMillis = millis) }
    }

    /** Valide la page 1 (type/personne/compte/montant/dates) ; avance à la page 2 si tout est
     * valide, affiche les erreurs correspondantes sinon. */
    fun goToStep2() {
        val state = _formState.value
        val amountMinor = Money.parseToMinorUnits(state.amountInput)

        val personError = if (state.personId == 0L) "Choisis une personne" else null
        val accountError = if (state.accountId == 0L) "Choisis un compte" else null
        val amountError = if (amountMinor == null || amountMinor <= 0L) "Montant invalide" else null
        val dueDateError = if (state.dueDateMillis <= state.startDateMillis) {
            "L'échéance doit être après la date de début"
        } else {
            null
        }

        if (personError != null || accountError != null || amountError != null || dueDateError != null) {
            _formState.update {
                it.copy(
                    personError = personError,
                    accountError = accountError,
                    amountError = amountError,
                    dueDateError = dueDateError
                )
            }
            return
        }

        _formState.update { it.copy(step = 2) }
    }

    fun backToStep1() {
        _formState.update { it.copy(step = 1) }
    }

    fun save() {
        val state = _formState.value
        // Déjà validé par goToStep2 pour atteindre cette page ; re-vérifié par sécurité (l'état
        // n'est normalement plus modifiable entre-temps, la page 1 n'étant plus affichée).
        val amountMinor = Money.parseToMinorUnits(state.amountInput) ?: return

        var firstPaymentMinor: Long? = null
        if (state.firstPaymentAmountInput.isNotBlank()) {
            val parsed = Money.parseToMinorUnits(state.firstPaymentAmountInput)
            if (parsed == null || parsed <= 0L) {
                _formState.update { it.copy(firstPaymentAmountError = "Montant invalide") }
                return
            }
            if (parsed > amountMinor) {
                _formState.update { it.copy(firstPaymentAmountError = "Ne peut pas dépasser le montant total") }
                return
            }
            firstPaymentMinor = parsed
        }

        _formState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val loanId = loanRepository.saveLoan(
                Loan(
                    personId = state.personId,
                    accountId = state.accountId,
                    type = state.type,
                    amount = amountMinor,
                    amountRepaid = 0L,
                    remainingAmount = amountMinor,
                    startDate = state.startDateMillis,
                    dueDate = state.dueDateMillis,
                    reason = LoanReason.OTHER,
                    reasonCustomText = null,
                    repaymentMode = RepaymentMode.SINGLE,
                    description = state.description.trim(),
                    // Recalculé par LoanRepositoryImpl.saveLoan : valeur ici sans importance.
                    status = LoanStatus.ONGOING,
                    createdAt = now,
                    updatedAt = now,
                    // Recalculé par LoanRepositoryImpl.saveLoan (transaction de décaissement créée
                    // dans la même opération atomique) : valeur ici sans importance.
                    transactionId = 0L
                )
            )

            firstPaymentMinor?.let { amount ->
                loanRepository.recordPayment(
                    LoanPayment(
                        loanId = loanId,
                        accountId = state.accountId,
                        amount = amount,
                        date = state.firstPaymentDateMillis,
                        note = "",
                        // Recalculé par LoanRepositoryImpl.recordPayment.
                        transactionId = 0L,
                        createdAt = now
                    )
                )
            }

            _formState.update { it.copy(isSaving = false) }
            _events.emit(LoanFormEvent.Saved)
        }
    }
}
