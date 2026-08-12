package com.arzikina.ne.presentation.utilities.recurring

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.PaymentMethod
import com.arzikina.ne.domain.model.RecurringFrequency
import com.arzikina.ne.domain.model.RecurringTransaction
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.CategoryRepository
import com.arzikina.ne.domain.repository.RecurringTransactionRepository
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * État du formulaire de création/édition d'une règle récurrente (voir [RecurringTransaction]).
 *
 * [isActive] n'est PAS exposé dans l'interface de ce formulaire (pas de bascule Actif/Inactif pour
 * cette première version) : chargé tel quel en mode édition et renvoyé inchangé à
 * `RecurringTransactionRepository.saveRecurringTransaction`, pour ne jamais réactiver
 * silencieusement une règle terminée ([RecurringFrequency.ONCE] déjà traitée, ou dont la
 * `endDate` est dépassée) simplement en modifiant un autre champ — voir la doc de cette méthode.
 *
 * `RecurringTransaction.id == 0L` fait office de sentinelle "nouvelle règle" (même convention que
 * [com.arzikina.ne.domain.model.Budget.id]).
 */
data class RecurringTransactionFormState(
    val type: TransactionType = TransactionType.EXPENSE,
    val amountInput: String = "",
    val accountId: Long = 0L,
    val categoryId: Long = 0L,
    val description: String = "",
    val paymentMethod: PaymentMethod? = null,
    val startDate: Long = System.currentTimeMillis(),
    val hasEndDate: Boolean = false,
    val endDate: Long = System.currentTimeMillis(),
    val frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    val isActive: Boolean = true,
    val createdAt: Long? = null,
    val amountError: String? = null,
    val accountError: String? = null,
    val categoryError: String? = null,
    val endDateError: String? = null
)

sealed interface RecurringTransactionFormEvent {
    data object Saved : RecurringTransactionFormEvent
    data object Deleted : RecurringTransactionFormEvent
}

@HiltViewModel
class RecurringTransactionFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recurringTransactionRepository: RecurringTransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository
) : ViewModel() {

    private val recurringTransactionId: Long = savedStateHandle.get<Long>(RECURRING_TRANSACTION_ID_ARG) ?: 0L
    val isEditMode: Boolean = recurringTransactionId != 0L

    private val _formState = MutableStateFlow(RecurringTransactionFormState())
    val formState: StateFlow<RecurringTransactionFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<RecurringTransactionFormEvent>()
    val events: SharedFlow<RecurringTransactionFormEvent> = _events.asSharedFlow()

    /** Chargés une fois : la liste des comptes ne dépend pas du reste du formulaire (voir
     * `TransactionFormViewModel.accounts`, même principe). */
    val accounts: StateFlow<List<Account>> = accountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Recalculée à chaque changement de type (revenu/dépense) — voir `TransactionFormViewModel.categories`,
     * même principe. Pas de filtrage des catégories système Prêts/Emprunts ici : une transaction
     * récurrente n'a aucun lien avec cette fonctionnalité (voir la doc de [RecurringTransaction]). */
    val categories: StateFlow<List<Category>> = _formState
        .map { it.type }
        .distinctUntilChanged()
        .flatMapLatest { type -> categoryRepository.observeCategoriesByType(type) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Solde COURANT de chaque compte (voir `TransactionFormViewModel.accountBalances`, même principe). */
    val accountBalances: StateFlow<Map<Long, Long>> = combine(
        accounts,
        transactionRepository.observeTransactions()
    ) { accounts, transactions -> computeCurrentBalances(accounts, transactions) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        if (isEditMode) {
            viewModelScope.launch {
                recurringTransactionRepository.getRecurringTransaction(recurringTransactionId)?.let { rule ->
                    _formState.update {
                        it.copy(
                            type = rule.type,
                            amountInput = Money.formatMajorUnits(rule.amount),
                            accountId = rule.accountId,
                            categoryId = rule.categoryId ?: 0L,
                            description = rule.description,
                            paymentMethod = rule.paymentMethod,
                            startDate = rule.startDate,
                            hasEndDate = rule.endDate != null,
                            endDate = rule.endDate ?: rule.startDate,
                            frequency = rule.frequency,
                            isActive = rule.isActive,
                            createdAt = rule.createdAt
                        )
                    }
                }
            }
        }
    }

    fun onTypeChange(type: TransactionType) {
        // Une catégorie de dépense n'a pas de sens pour un revenu (et inversement, voir
        // `Category.type`) : remise à zéro pour forcer un nouveau choix cohérent avec [categories],
        // même principe que `TransactionFormViewModel`.
        _formState.update { it.copy(type = type, categoryId = 0L, categoryError = null) }
    }

    fun onAmountChange(value: String) {
        _formState.update { it.copy(amountInput = value, amountError = null) }
    }

    fun onAccountChange(accountId: Long) {
        _formState.update { it.copy(accountId = accountId, accountError = null) }
    }

    fun onCategoryChange(categoryId: Long) {
        _formState.update { it.copy(categoryId = categoryId, categoryError = null) }
    }

    fun onDescriptionChange(value: String) {
        _formState.update { it.copy(description = value) }
    }

    fun onPaymentMethodChange(method: PaymentMethod?) {
        _formState.update { it.copy(paymentMethod = method) }
    }

    fun onStartDateChange(millis: Long) {
        _formState.update { it.copy(startDate = millis, endDateError = null) }
    }

    fun onEndDateToggle(enabled: Boolean) {
        _formState.update { it.copy(hasEndDate = enabled, endDateError = null) }
    }

    fun onEndDateChange(millis: Long) {
        _formState.update { it.copy(endDate = millis, endDateError = null) }
    }

    fun onFrequencyChange(frequency: RecurringFrequency) {
        _formState.update { it.copy(frequency = frequency) }
    }

    fun save() {
        val state = _formState.value

        val amountMinor = Money.parseToMinorUnits(state.amountInput)
        val amountValid = amountMinor != null && amountMinor > 0L
        val accountValid = state.accountId != 0L
        val categoryValid = state.categoryId != 0L
        val endDateValid = !state.hasEndDate || isAfterDay(state.endDate, state.startDate) || isSameDay(state.endDate, state.startDate)

        // Messages en dur (pas de `Context` dans ce ViewModel) : même convention que
        // `TransactionFormViewModel.save`/`BudgetFormViewModel.save`, seuls précédents de
        // validation de formulaire déjà établis dans ce projet.
        if (!amountValid || !accountValid || !categoryValid || !endDateValid) {
            _formState.update {
                it.copy(
                    amountError = if (!amountValid) "Montant invalide" else null,
                    accountError = if (!accountValid) "Choisis un compte" else null,
                    categoryError = if (!categoryValid) "Choisis une catégorie" else null,
                    endDateError = if (!endDateValid) "La date de fin doit être après la date de début" else null
                )
            }
            return
        }

        viewModelScope.launch {
            recurringTransactionRepository.saveRecurringTransaction(
                RecurringTransaction(
                    id = recurringTransactionId,
                    type = state.type,
                    amount = amountMinor!!,
                    accountId = state.accountId,
                    categoryId = state.categoryId,
                    description = state.description,
                    paymentMethod = state.paymentMethod,
                    startDate = state.startDate,
                    endDate = state.endDate.takeIf { state.hasEndDate },
                    frequency = state.frequency,
                    // `nextExecutionDate` : valeur ignorée/recalculée par le repository (voir la doc
                    // de `saveRecurringTransaction`), jamais lue depuis ce formulaire.
                    nextExecutionDate = state.startDate,
                    isActive = state.isActive,
                    createdAt = state.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            _events.emit(RecurringTransactionFormEvent.Saved)
        }
    }

    fun delete() {
        if (!isEditMode) return
        viewModelScope.launch {
            recurringTransactionRepository.deleteRecurringTransaction(recurringTransactionId)
            _events.emit(RecurringTransactionFormEvent.Deleted)
        }
    }

    private fun isAfterDay(date: Long, reference: Long): Boolean = toLocalDate(date).isAfter(toLocalDate(reference))
    private fun isSameDay(date: Long, reference: Long): Boolean = toLocalDate(date) == toLocalDate(reference)
    private fun toLocalDate(epochMillis: Long) = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

    private companion object {
        const val RECURRING_TRANSACTION_ID_ARG = "recurringTransactionId"
    }
}
