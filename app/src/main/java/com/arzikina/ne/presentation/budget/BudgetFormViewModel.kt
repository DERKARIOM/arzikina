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
import com.arzikina.ne.util.BudgetPeriodStatus
import com.arzikina.ne.util.Constants
import com.arzikina.ne.util.DatePeriods
import com.arzikina.ne.util.Money
import com.arzikina.ne.util.QuickDateRange
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
import java.time.LocalDate
import javax.inject.Inject

/**
 * État du formulaire d'ajout/édition de budget.
 *
 * `Budget.id == 0L` fait office de sentinelle "nouveau budget" (même
 * convention que [Budget.id]).
 *
 * [isLegacyRecurring] distingue les deux modes mutuellement exclusifs de la doc de [Budget] :
 * `true` uniquement en édition d'un budget créé avant la fonctionnalité "période fixe" (`false` par
 * défaut, donc pour tout nouveau budget — voir cahier des charges "Amélioration de la
 * fonctionnalité Budget — Gestion d'une période", remplacement recommandé). [period] reste utilisé
 * tel quel quand [isLegacyRecurring] vaut `true` ; ignoré sinon (voir [startDate]/[endDate]).
 */
data class BudgetFormState(
    val categoryId: Long = 0L,
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val isLegacyRecurring: Boolean = false,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val quickRange: QuickDateRange? = null,
    val dateError: String? = null,
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
     * Catégories de dépense sans budget ACTIF, plus celle déjà assignée à ce
     * budget en mode édition. Depuis la version 19 (période fixe, voir
     * [Budget]), l'index `categoryId` de `BudgetEntity` n'est plus unique : une
     * catégorie peut avoir plusieurs budgets successifs (un par période), donc
     * ce n'est plus "toute catégorie déjà utilisée" qui est exclue, mais
     * seulement celle dont un budget est encore actif (voir [isActive]) — un
     * budget Terminé libère sa catégorie pour un nouveau budget.
     */
    val availableCategories: StateFlow<List<Category>> = combine(
        categoryRepository.observeCategoriesByType(TransactionType.EXPENSE),
        budgetRepository.observeBudgets()
    ) { categories, budgets ->
        val blockedCategoryIds = budgets
            .filter { it.id != budgetId && isActive(it) }
            .map { it.categoryId }
            .toSet()
        categories.filter { it.id !in blockedCategoryIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (isEditMode) {
            viewModelScope.launch {
                budgetRepository.getBudget(budgetId)?.let { budget ->
                    _formState.update {
                        it.copy(
                            categoryId = budget.categoryId,
                            period = budget.period,
                            // Jamais un seul des deux nul (voir Budget) : startDate suffit à
                            // distinguer les deux modes.
                            isLegacyRecurring = budget.startDate == null,
                            startDate = budget.startDate,
                            endDate = budget.endDate,
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

    /** Calcule et applique les dates littérales du raccourci (voir [QuickDateRange], doc de tête :
     * figées à l'appel, ne "glissent" plus ensuite). */
    fun onQuickRangeSelected(range: QuickDateRange) {
        val (start, end) = range.toDateRange()
        _formState.update {
            it.copy(
                quickRange = range,
                startDate = DatePeriods.toEpochMillis(start),
                endDate = DatePeriods.toEpochMillis(end),
                dateError = null
            )
        }
    }

    /** "Personnalisée" : ne touche pas aux dates déjà choisies, seulement à l'état visuel du
     * ToggleGroup (voir `BudgetFormFragment.render`) — l'utilisateur les affine ensuite via
     * [onStartDateChange]/[onEndDateChange]. */
    fun onCustomRangeSelected() {
        _formState.update { it.copy(quickRange = null) }
    }

    /** Une modification manuelle d'une date invalide tout raccourci actif : voir [onQuickRangeSelected]. */
    fun onStartDateChange(millis: Long) {
        _formState.update { it.copy(startDate = millis, quickRange = null, dateError = null) }
    }

    fun onEndDateChange(millis: Long) {
        _formState.update { it.copy(endDate = millis, quickRange = null, dateError = null) }
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
        // Récurrent (legacy) : aucune date à valider, period fait foi (voir isLegacyRecurring).
        val dateError = if (!state.isLegacyRecurring) {
            when {
                state.startDate == null || state.endDate == null -> "Choisis une période"
                state.endDate < state.startDate -> "La date de fin doit être après ou égale à la date de début"
                else -> null
            }
        } else {
            null
        }
        if (dateError != null) {
            _formState.update { it.copy(dateError = dateError) }
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
                    createdAt = state.createdAt ?: System.currentTimeMillis(),
                    startDate = if (state.isLegacyRecurring) null else state.startDate,
                    endDate = if (state.isLegacyRecurring) null else state.endDate
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

    /**
     * Un budget "occupe" sa catégorie tant qu'il n'est pas Terminé : toujours vrai pour un budget
     * récurrent legacy ([BudgetPeriodStatus.of] retourne `null`, voir sa doc), vrai pour À venir et
     * En cours sinon — seul [BudgetPeriodStatus.COMPLETED] libère la catégorie. Même statut que
     * celui affiché sur la carte de la liste (voir `BudgetAdapter`) : pas de règle dupliquée.
     */
    private fun isActive(budget: Budget, today: LocalDate = LocalDate.now()): Boolean =
        BudgetPeriodStatus.of(budget.startDate, budget.endDate, today) != BudgetPeriodStatus.COMPLETED

    private companion object {
        const val BUDGET_ID_ARG = "budgetId"
    }
}
