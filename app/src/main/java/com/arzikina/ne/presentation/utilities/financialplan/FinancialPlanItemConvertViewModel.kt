package com.arzikina.ne.presentation.utilities.financialplan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.PlanItemStatus
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.CategoryRepository
import com.arzikina.ne.domain.repository.FinancialPlanRepository
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
import javax.inject.Inject

/**
 * État de l'écran "Enregistrer comme transaction" (voir cahier des charges "Planification
 * financière", section 12) — convertit UNE dépense prévue en [com.arzikina.ne.domain.model.Transaction]
 * réelle, sur action EXPLICITE de l'utilisateur (voir [save]/[FinancialPlanRepository.convertItemToTransaction]).
 *
 * Tous les champs sont PRÉ-REMPLIS depuis la dépense prévue (nom → description, montant prévu →
 * montant réel suggéré, catégorie si déjà choisie, date prévue si renseignée sinon aujourd'hui)
 * mais restent modifiables : le montant réellement dépensé peut différer du montant prévu (voir
 * [com.arzikina.ne.domain.model.FinancialPlanItem.actualAmount]), et rien n'empêche de changer de
 * catégorie/date au moment de la conversion.
 *
 * [accountId] N'A PAS de pré-remplissage (contrairement à `TransactionFormViewModel.presetAccountId`,
 * réservé à "Détail du compte") : une planification n'est reliée à aucun compte précis (voir la
 * doc de [com.arzikina.ne.domain.model.FinancialPlan.availableAmount]), l'utilisateur doit donc
 * toujours choisir explicitement le compte à débiter.
 */
data class FinancialPlanItemConvertState(
    val isLoaded: Boolean = false,
    /** `true` si `itemId` ne correspond à aucune dépense prévue existante, si elle a déjà été
     * convertie (voir [com.arzikina.ne.domain.model.FinancialPlanItem.transactionId]), ou si elle
     * est [com.arzikina.ne.domain.model.PlanItemStatus.CANCELLED] (Étape 11 : une dépense annulée
     * n'a plus lieu d'être honorée) — même raisonnement que `LoanPaymentFormState.notFound`. */
    val notFound: Boolean = false,
    val itemName: String = "",
    val plannedAmount: Long = 0L,
    val accountId: Long = 0L,
    val categoryId: Long = 0L,
    val actualAmountInput: String = "",
    val descriptionInput: String = "",
    val dateMillis: Long = System.currentTimeMillis(),
    val accountError: String? = null,
    val categoryError: String? = null,
    val amountError: String? = null,
    val isSaving: Boolean = false
)

sealed interface FinancialPlanItemConvertEvent {
    data object Saved : FinancialPlanItemConvertEvent
}

@HiltViewModel
class FinancialPlanItemConvertViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val financialPlanRepository: FinancialPlanRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository
) : ViewModel() {

    private val itemId: Long = savedStateHandle.get<Long>(ITEM_ID_ARG) ?: 0L

    private val _formState = MutableStateFlow(FinancialPlanItemConvertState())
    val formState: StateFlow<FinancialPlanItemConvertState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<FinancialPlanItemConvertEvent>()
    val events: SharedFlow<FinancialPlanItemConvertEvent> = _events.asSharedFlow()

    val accounts: StateFlow<List<Account>> = accountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyList())

    /** Voir `LoanPaymentFormViewModel.accountBalances` pour le même raisonnement : le sélecteur de
     * compte ([com.arzikina.ne.presentation.components.AccountPickerDialog]) affiche le solde
     * COURANT de chaque compte, pas [Account.initialBalance]. */
    val accountBalances: StateFlow<Map<Long, Long>> = combine(
        accounts,
        transactionRepository.observeTransactions()
    ) { accounts, transactions -> computeCurrentBalances(accounts, transactions) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyMap())

    /** Catégories de DÉPENSE uniquement (voir `FinancialPlanItemFormViewModel.categories` pour le
     * même raisonnement) : cette conversion crée toujours une [TransactionType.EXPENSE]. */
    val categories: StateFlow<List<Category>> = categoryRepository.observeCategoriesByType(TransactionType.EXPENSE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyList())

    init {
        viewModelScope.launch {
            val item = financialPlanRepository.getItem(itemId)
            // Étape 11 : une dépense CANCELLED ne peut plus être convertie (voir la doc de
            // FinancialPlanRepository.convertItemToTransaction) — même traitement que "déjà
            // convertie" ou "introuvable", cet écran ne doit simplement jamais s'ouvrir dans ce cas.
            if (item == null || item.transactionId != null || item.status == PlanItemStatus.CANCELLED) {
                _formState.update { it.copy(notFound = true) }
                return@launch
            }
            _formState.update {
                it.copy(
                    isLoaded = true,
                    itemName = item.name,
                    plannedAmount = item.amount,
                    categoryId = item.categoryId ?: 0L,
                    actualAmountInput = Money.formatMajorUnits(item.amount),
                    descriptionInput = item.name,
                    dateMillis = item.plannedDate ?: System.currentTimeMillis()
                )
            }
        }
    }

    fun onAccountChange(account: Account) {
        _formState.update { it.copy(accountId = account.id, accountError = null) }
    }

    fun onCategoryChange(category: Category?) {
        _formState.update { it.copy(categoryId = category?.id ?: 0L, categoryError = null) }
    }

    fun onActualAmountChange(value: String) {
        _formState.update { it.copy(actualAmountInput = value, amountError = null) }
    }

    fun onDescriptionChange(value: String) {
        _formState.update { it.copy(descriptionInput = value) }
    }

    fun onDateChange(millis: Long) {
        _formState.update { it.copy(dateMillis = millis) }
    }

    fun save() {
        val state = _formState.value
        if (state.isSaving) return

        val accountError = if (state.accountId == 0L) "Choisis un compte" else null
        val categoryError = if (state.categoryId == 0L) "Choisis une catégorie" else null
        val amountMinor = Money.parseToMinorUnits(state.actualAmountInput)
        val amountError = if (amountMinor == null || amountMinor <= 0L) "Montant invalide" else null

        if (accountError != null || categoryError != null || amountError != null) {
            _formState.update { it.copy(accountError = accountError, categoryError = categoryError, amountError = amountError) }
            return
        }
        checkNotNull(amountMinor)

        _formState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            financialPlanRepository.convertItemToTransaction(
                itemId = itemId,
                accountId = state.accountId,
                categoryId = state.categoryId,
                actualAmount = amountMinor,
                date = state.dateMillis,
                description = state.descriptionInput.trim()
            )
            _formState.update { it.copy(isSaving = false) }
            _events.emit(FinancialPlanItemConvertEvent.Saved)
        }
    }

    private companion object {
        const val ITEM_ID_ARG = "itemId"
    }
}
