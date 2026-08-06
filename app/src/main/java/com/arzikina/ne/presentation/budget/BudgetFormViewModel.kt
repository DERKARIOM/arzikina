package com.arzikina.ne.presentation.budget

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Budget
import com.arzikina.ne.domain.model.BudgetPeriod
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.BudgetRepository
import com.arzikina.ne.domain.repository.CategoryRepository
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
 * État du formulaire d'ajout/édition de budget.
 *
 * `Budget.id == 0L` fait office de sentinelle "nouveau budget" (même
 * convention que [Budget.id]).
 */
data class BudgetFormState(
    val categoryId: Long = 0L,
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val limitInput: String = "",
    val currencyCode: String = Constants.DEFAULT_CURRENCY_CODE,
    val createdAt: Long? = null,
    val categoryError: String? = null,
    val limitError: String? = null
)

sealed interface BudgetFormEvent {
    data object Saved : BudgetFormEvent
    data object Deleted : BudgetFormEvent
}

@HiltViewModel
class BudgetFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    private val budgetId: Long = savedStateHandle.get<Long>(BUDGET_ID_ARG) ?: 0L
    val isEditMode: Boolean = budgetId != 0L

    private val _formState = MutableStateFlow(BudgetFormState())
    val formState: StateFlow<BudgetFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<BudgetFormEvent>()
    val events: SharedFlow<BudgetFormEvent> = _events.asSharedFlow()

    /**
     * Catégories de dépense sans budget actif, plus celle déjà assignée à ce
     * budget en mode édition (contrainte d'unicité posée par l'index
     * `categoryId` de `BudgetEntity`).
     */
    val availableCategories: StateFlow<List<Category>> = combine(
        categoryRepository.observeCategoriesByType(TransactionType.EXPENSE),
        budgetRepository.observeBudgets()
    ) { categories, budgets ->
        val usedCategoryIds = budgets.filter { it.id != budgetId }.map { it.categoryId }.toSet()
        categories.filter { it.id !in usedCategoryIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (isEditMode) {
            viewModelScope.launch {
                budgetRepository.getBudget(budgetId)?.let { budget ->
                    _formState.update {
                        it.copy(
                            categoryId = budget.categoryId,
                            period = budget.period,
                            limitInput = Money.formatMajorUnits(budget.limitAmount),
                            currencyCode = budget.currencyCode,
                            createdAt = budget.createdAt
                        )
                    }
                }
            }
        }
    }

    fun onCategoryChange(categoryId: Long) {
        _formState.update { it.copy(categoryId = categoryId, categoryError = null) }
    }

    fun onPeriodChange(period: BudgetPeriod) {
        _formState.update { it.copy(period = period) }
    }

    fun onLimitChange(value: String) {
        _formState.update { it.copy(limitInput = value, limitError = null) }
    }

    fun onCurrencyChange(code: String) {
        _formState.update { it.copy(currencyCode = code) }
    }

    fun save() {
        val state = _formState.value

        if (state.categoryId == 0L) {
            _formState.update { it.copy(categoryError = "Choisis une catégorie") }
            return
        }
        val limitMinor = Money.parseToMinorUnits(state.limitInput)
        if (limitMinor == null || limitMinor <= 0L) {
            _formState.update { it.copy(limitError = "Plafond invalide") }
            return
        }

        viewModelScope.launch {
            budgetRepository.saveBudget(
                Budget(
                    id = budgetId,
                    categoryId = state.categoryId,
                    period = state.period,
                    limitAmount = limitMinor,
                    currencyCode = state.currencyCode,
                    createdAt = state.createdAt ?: System.currentTimeMillis()
                )
            )
            _events.emit(BudgetFormEvent.Saved)
        }
    }

    fun delete() {
        if (!isEditMode) return
        viewModelScope.launch {
            budgetRepository.deleteBudget(budgetId)
            _events.emit(BudgetFormEvent.Deleted)
        }
    }

    private companion object {
        const val BUDGET_ID_ARG = "budgetId"
    }
}
