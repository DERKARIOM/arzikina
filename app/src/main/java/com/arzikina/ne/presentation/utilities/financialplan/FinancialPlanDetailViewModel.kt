package com.arzikina.ne.presentation.utilities.financialplan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.FinancialPlan
import com.arzikina.ne.domain.model.FinancialPlanItem
import com.arzikina.ne.domain.repository.CategoryRepository
import com.arzikina.ne.domain.repository.FinancialPlanRepository
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.FinancialPlanProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * État affiché par l'écran "Détail de la planification" — résumé (voir [FinancialPlanProgress])
 * + dépenses prévues de CETTE planification uniquement.
 *
 * [categoriesById] : TOUTES les catégories (pas seulement celles de type dépense utilisées par le
 * formulaire, voir `FinancialPlanItemFormViewModel.categories`) — une dépense prévue peut avoir
 * été catégorisée avant un changement de type de catégorie ; simple table de correspondance pour
 * l'affichage, voir `FinancialPlanDetailAdapter.ItemViewHolder`.
 */
data class FinancialPlanDetailUiState(
    val plan: FinancialPlan,
    val items: List<FinancialPlanItem>,
    val totalPlanned: Long,
    val remainingAmount: Long,
    val progressPercent: Int,
    val isOverBudget: Boolean,
    val categoriesById: Map<Long, Category>
)

@HiltViewModel
class FinancialPlanDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val financialPlanRepository: FinancialPlanRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    val planId: Long = savedStateHandle.get<Long>(PLAN_ID_ARG) ?: 0L

    /** Même raisonnement que `LoanDetailViewModel.uiState` : `AppResult.Error` si la planification
     * a été supprimée depuis un autre écran (ex. suppression rapide depuis [FinancialPlansFragment])
     * pendant que cet écran restait ouvert — [FinancialPlanDetailFragment] revient alors en arrière
     * plutôt que de rester figé. */
    val uiState: StateFlow<AppResult<FinancialPlanDetailUiState>> = combine(
        financialPlanRepository.observePlans(),
        financialPlanRepository.observeItems(planId),
        categoryRepository.observeCategories()
    ) { plans, items, categories ->
        val plan = plans.find { it.id == planId } ?: return@combine null
        val totalPlanned = FinancialPlanProgress.calculateTotalPlanned(items)
        FinancialPlanDetailUiState(
            plan = plan,
            items = items,
            totalPlanned = totalPlanned,
            remainingAmount = FinancialPlanProgress.calculateRemainingAmount(plan.availableAmount, totalPlanned),
            progressPercent = FinancialPlanProgress.calculateProgress(plan.availableAmount, totalPlanned),
            isOverBudget = FinancialPlanProgress.calculateOverBudget(plan.availableAmount, totalPlanned),
            categoriesById = categories.associateBy { it.id }
        )
    }
        .map<FinancialPlanDetailUiState?, AppResult<FinancialPlanDetailUiState>> { state ->
            state?.let { AppResult.Success(it) } ?: AppResult.Error("Planification introuvable")
        }
        .catch { throwable -> emit(AppResult.Error(throwable.message ?: "Erreur inconnue", throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )

    fun deletePlan() {
        viewModelScope.launch {
            financialPlanRepository.deletePlan(planId)
        }
    }

    fun deleteItem(itemId: Long) {
        viewModelScope.launch {
            financialPlanRepository.deleteItem(itemId)
        }
    }

    private companion object {
        const val PLAN_ID_ARG = "planId"
    }
}
