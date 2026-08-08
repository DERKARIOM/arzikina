package com.arzikina.ne.presentation.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.PaymentMethod
import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.CategoryRepository
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * État du formulaire d'ajout/édition de transaction.
 *
 * `Transaction.id == 0L` fait office de sentinelle "nouvelle transaction"
 * (même convention que [Transaction.id]). `dateTimeMillis` fusionne date et
 * heure : le formulaire les édite séparément ([DatePickerField]/
 * [TimePickerField]) mais le domaine ne stocke qu'un seul instant.
 */
data class TransactionFormState(
    val amountInput: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val accountId: Long = 0L,
    val categoryId: Long = 0L,
    val dateTimeMillis: Long = System.currentTimeMillis(),
    val description: String = "",
    val paymentMethod: PaymentMethod? = null,
    val createdAt: Long? = null,
    val amountError: String? = null,
    val accountError: String? = null,
    val categoryError: String? = null
)

sealed interface TransactionFormEvent {
    data object Saved : TransactionFormEvent
    data object Deleted : TransactionFormEvent
}

@HiltViewModel
class TransactionFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val transactionId: Long = savedStateHandle.get<Long>(TRANSACTION_ID_ARG) ?: 0L
    val isEditMode: Boolean = transactionId != 0L

    private val _formState = MutableStateFlow(TransactionFormState())
    val formState: StateFlow<TransactionFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<TransactionFormEvent>()
    val events: SharedFlow<TransactionFormEvent> = _events.asSharedFlow()

    /** Chargés une fois : la liste des comptes ne dépend pas du reste du formulaire. */
    val accounts: StateFlow<List<Account>> = accountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Recalculée à chaque changement de type (revenu/dépense), comme sur l'écran Catégories. */
    val categories: StateFlow<List<Category>> = _formState
        .map { it.type }
        .distinctUntilChanged()
        .flatMapLatest { type -> categoryRepository.observeCategoriesByType(type) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Solde COURANT de chaque compte (voir [computeCurrentBalances]), affiché
     * sur la ligne "Compte" et dans [com.arzikina.ne.presentation.components.AccountPickerDialog] —
     * pas [Account.initialBalance].
     */
    val accountBalances: StateFlow<Map<Long, Long>> = combine(
        accounts,
        transactionRepository.observeTransactions()
    ) { accounts, transactions -> computeCurrentBalances(accounts, transactions) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        if (isEditMode) {
            viewModelScope.launch {
                transactionRepository.getTransaction(transactionId)?.let { transaction ->
                    _formState.update {
                        it.copy(
                            amountInput = Money.formatMajorUnits(transaction.amount),
                            type = transaction.type,
                            accountId = transaction.accountId,
                            categoryId = transaction.categoryId,
                            dateTimeMillis = transaction.date,
                            description = transaction.description,
                            paymentMethod = transaction.paymentMethod,
                            createdAt = transaction.createdAt
                        )
                    }
                }
            }
        } else {
            // Pré-remplit le compte quand ce formulaire est ouvert depuis
            // "Détail du compte" (voir AccountDetailFragment.navigateToNewTransactionForm) :
            // 0L = "aucun compte présélectionné", même convention que transactionId.
            val presetAccountId = savedStateHandle.get<Long>(PRESET_ACCOUNT_ID_ARG) ?: 0L
            if (presetAccountId != 0L) {
                _formState.update { it.copy(accountId = presetAccountId) }
            }
        }
    }

    fun onAmountChange(value: String) {
        _formState.update { it.copy(amountInput = value, amountError = null) }
    }

    /**
     * Raccourci rapide (voir maquette "PERSONNALISATION – AJOUT DE
     * TRANSACTION", +1000/+5000/+10000) : AJOUTE [majorUnits] au montant déjà
     * saisi plutôt que de le remplacer, pour permettre de cumuler plusieurs
     * raccourcis (ex. +5000 puis +1000 pour 6000). Un montant vide ou invalide
     * est traité comme 0.
     */
    fun onQuickAmountAdd(majorUnits: Long) {
        _formState.update { state ->
            val currentMinor = Money.parseToMinorUnits(state.amountInput) ?: 0L
            val newMinor = currentMinor + majorUnits * 100
            state.copy(amountInput = Money.formatMajorUnits(newMinor), amountError = null)
        }
    }

    fun onTypeChange(type: TransactionType) {
        // La catégorie précédente peut ne plus correspondre au nouveau type.
        _formState.update { it.copy(type = type, categoryId = 0L, categoryError = null) }
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

    fun onDateChange(date: LocalDate) {
        _formState.update { state ->
            val currentTime = Instant.ofEpochMilli(state.dateTimeMillis).atZone(ZoneId.systemDefault()).toLocalTime()
            state.copy(dateTimeMillis = date.atTime(currentTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
    }

    fun onTimeChange(time: LocalTime) {
        _formState.update { state ->
            val currentDate = Instant.ofEpochMilli(state.dateTimeMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            state.copy(dateTimeMillis = currentDate.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
    }

    fun save() {
        val state = _formState.value

        val amountMinor = Money.parseToMinorUnits(state.amountInput)
        if (amountMinor == null || amountMinor <= 0L) {
            _formState.update { it.copy(amountError = "Montant invalide") }
            return
        }
        if (state.accountId == 0L) {
            _formState.update { it.copy(accountError = "Choisis un compte") }
            return
        }
        if (state.categoryId == 0L) {
            _formState.update { it.copy(categoryError = "Choisis une catégorie") }
            return
        }

        viewModelScope.launch {
            transactionRepository.saveTransaction(
                Transaction(
                    id = transactionId,
                    amount = amountMinor,
                    type = state.type,
                    accountId = state.accountId,
                    categoryId = state.categoryId,
                    date = state.dateTimeMillis,
                    description = state.description.trim(),
                    paymentMethod = state.paymentMethod,
                    createdAt = state.createdAt ?: System.currentTimeMillis()
                )
            )
            _events.emit(TransactionFormEvent.Saved)
        }
    }

    fun delete() {
        if (!isEditMode) return
        viewModelScope.launch {
            transactionRepository.deleteTransaction(transactionId)
            _events.emit(TransactionFormEvent.Deleted)
        }
    }

    private companion object {
        const val TRANSACTION_ID_ARG = "transactionId"
        const val PRESET_ACCOUNT_ID_ARG = "presetAccountId"
    }
}
