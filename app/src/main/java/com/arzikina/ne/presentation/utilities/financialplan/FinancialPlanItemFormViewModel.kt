package com.arzikina.ne.presentation.utilities.financialplan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.FinancialPlanItem
import com.arzikina.ne.domain.model.PlanItemPriority
import com.arzikina.ne.domain.model.PlanItemStatus
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.CategoryRepository
import com.arzikina.ne.domain.repository.FinancialPlanRepository
import com.arzikina.ne.util.FinancialPlanProgress
import com.arzikina.ne.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * État du formulaire "Ajouter/Modifier une dépense prévue" (voir cahier des charges "Planification
 * financière", section 9) — création ET édition à cette étape (voir [isEditMode]), contrairement à
 * l'Étape 4 (création seulement, nom+montant seulement).
 *
 * IMPORTANT — rappel du principe fondamental (voir la doc de
 * [com.arzikina.ne.domain.model.FinancialPlan]) : [save] n'appelle QUE
 * [FinancialPlanRepository.saveItem], qui ne touche jamais `TransactionDao`/`AccountDao`. Modifier
 * l'état d'une dépense prévue à [PlanItemStatus.DONE] ici reste purement déclaratif : ça ne crée
 * AUCUNE transaction réelle (voir cahier des charges section 12, "Enregistrer comme transaction" —
 * fonctionnalité distincte, étape ultérieure du plan).
 *
 * [categoryId] : `0L` = "aucune catégorie" (même convention que `TransactionFormState.categoryId`).
 * [hasDate]/[dateMillis] : date prévue OPTIONNELLE, même pattern que
 * `RecurringTransactionFormState.hasEndDate`/`endDate` (Switch qui révèle/masque le champ plutôt
 * qu'un bouton d'effacement, absent du reste du projet).
 */
data class FinancialPlanItemFormState(
    val isLoaded: Boolean = false,
    /** `true` si `planId` ne correspond à aucune planification existante, ou si `itemId` (mode
     * édition) ne correspond à aucune dépense prévue existante — même raisonnement que
     * `LoanPaymentFormState.notFound`. */
    val notFound: Boolean = false,
    val planName: String = "",
    val planRemainingAmount: Long = 0L,
    val nameInput: String = "",
    val amountInput: String = "",
    val categoryId: Long = 0L,
    val descriptionInput: String = "",
    val hasDate: Boolean = false,
    val dateMillis: Long = System.currentTimeMillis(),
    val priority: PlanItemPriority = PlanItemPriority.IMPORTANT,
    val status: PlanItemStatus = PlanItemStatus.TO_PLAN,
    /** `true` si cette dépense prévue a déjà été convertie en transaction réelle (voir
     * [FinancialPlanItem.transactionId]) — [FinancialPlanItemFormFragment] masque alors le bouton
     * "Enregistrer comme transaction" (une dépense ne se convertit jamais deux fois, voir
     * [FinancialPlanRepository.convertItemToTransaction]). Toujours `false` en création. */
    val isAlreadyConverted: Boolean = false,
    val nameError: String? = null,
    val amountError: String? = null,
    val isSaving: Boolean = false
)

sealed interface FinancialPlanItemFormEvent {
    data object Saved : FinancialPlanItemFormEvent
}

@HiltViewModel
class FinancialPlanItemFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val financialPlanRepository: FinancialPlanRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    private val planId: Long = savedStateHandle.get<Long>(PLAN_ID_ARG) ?: 0L
    /** Exposé (pas `private`) : [FinancialPlanItemFormFragment] en a besoin pour naviguer vers
     * [FinancialPlanItemConvertFragment] ("Enregistrer comme transaction"). */
    val itemId: Long = savedStateHandle.get<Long>(ITEM_ID_ARG) ?: 0L
    val isEditMode: Boolean = itemId != 0L

    private val _formState = MutableStateFlow(FinancialPlanItemFormState())
    val formState: StateFlow<FinancialPlanItemFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<FinancialPlanItemFormEvent>()
    val events: SharedFlow<FinancialPlanItemFormEvent> = _events.asSharedFlow()

    /** Catégories de DÉPENSE uniquement (voir [TransactionType.EXPENSE]) — une planification
     * répond à "quelles dépenses dois-je prévoir", même filtrage que `BudgetFormViewModel`. */
    val categories: StateFlow<List<Category>> = categoryRepository.observeCategoriesByType(TransactionType.EXPENSE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyList())

    /** Créée une seule fois avant tout accès (voir [createdAt]) : conservée pour ne pas perdre la
     * date de création réelle en mode édition (même raisonnement que
     * `FinancialPlanFormViewModel.existingPlan`). */
    private var createdAt: Long? = null
    private var transactionId: Long? = null

    init {
        viewModelScope.launch {
            val plan = financialPlanRepository.getPlan(planId)
            if (plan == null) {
                _formState.update { it.copy(notFound = true) }
                return@launch
            }
            // En édition, exclut la dépense en cours d'édition de son propre "reste disponible" —
            // sinon son propre montant se soustrairait deux fois (une fois déjà en base, une fois
            // en cours de saisie), affichant un reste artificiellement plus bas.
            val items = financialPlanRepository.observeItems(planId).first()
            val relevantItems = if (isEditMode) items.filterNot { it.id == itemId } else items
            val totalPlanned = FinancialPlanProgress.calculateTotalPlanned(relevantItems)
            val remainingAmount = FinancialPlanProgress.calculateRemainingAmount(plan.availableAmount, totalPlanned)

            if (isEditMode) {
                val item = financialPlanRepository.getItem(itemId)
                if (item == null || item.planId != planId) {
                    _formState.update { it.copy(notFound = true) }
                    return@launch
                }
                createdAt = item.createdAt
                transactionId = item.transactionId
                _formState.update {
                    it.copy(
                        isLoaded = true,
                        planName = plan.name,
                        planRemainingAmount = remainingAmount,
                        nameInput = item.name,
                        amountInput = Money.formatMajorUnits(item.amount),
                        categoryId = item.categoryId ?: 0L,
                        descriptionInput = item.description.orEmpty(),
                        hasDate = item.plannedDate != null,
                        dateMillis = item.plannedDate ?: System.currentTimeMillis(),
                        priority = item.priority,
                        status = item.status,
                        isAlreadyConverted = item.transactionId != null
                    )
                }
            } else {
                _formState.update {
                    it.copy(isLoaded = true, planName = plan.name, planRemainingAmount = remainingAmount)
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _formState.update { it.copy(nameInput = value, nameError = null) }
    }

    fun onAmountChange(value: String) {
        _formState.update { it.copy(amountInput = value, amountError = null) }
    }

    fun onCategoryChange(category: Category?) {
        _formState.update { it.copy(categoryId = category?.id ?: 0L) }
    }

    fun onDescriptionChange(value: String) {
        _formState.update { it.copy(descriptionInput = value) }
    }

    fun onDateToggle(enabled: Boolean) {
        _formState.update { it.copy(hasDate = enabled) }
    }

    fun onDateChange(millis: Long) {
        _formState.update { it.copy(dateMillis = millis) }
    }

    fun onPriorityChange(priority: PlanItemPriority) {
        _formState.update { it.copy(priority = priority) }
    }

    fun onStatusChange(status: PlanItemStatus) {
        _formState.update { it.copy(status = status) }
    }

    fun save() {
        val state = _formState.value
        if (state.isSaving) return

        val nameError = if (state.nameInput.isBlank()) "Nom requis" else null
        val amountMinor = Money.parseToMinorUnits(state.amountInput)
        val amountError = if (amountMinor == null || amountMinor <= 0L) "Montant invalide" else null

        if (nameError != null || amountError != null) {
            _formState.update { it.copy(nameError = nameError, amountError = amountError) }
            return
        }
        checkNotNull(amountMinor)

        _formState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            financialPlanRepository.saveItem(
                FinancialPlanItem(
                    id = itemId,
                    planId = planId,
                    name = state.nameInput.trim(),
                    amount = amountMinor,
                    categoryId = state.categoryId.takeIf { it != 0L },
                    description = state.descriptionInput.trim().ifEmpty { null },
                    plannedDate = state.dateMillis.takeIf { state.hasDate },
                    priority = state.priority,
                    status = state.status,
                    transactionId = transactionId,
                    createdAt = createdAt ?: now,
                    updatedAt = now
                )
            )
            _formState.update { it.copy(isSaving = false) }
            _events.emit(FinancialPlanItemFormEvent.Saved)
        }
    }

    private companion object {
        const val PLAN_ID_ARG = "planId"
        const val ITEM_ID_ARG = "itemId"
    }
}
