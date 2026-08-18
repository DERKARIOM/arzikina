package com.arzikina.ne.presentation.utilities.financialplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.FinancialPlan
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
 * Une planification avec sa progression déjà calculée, prête pour l'affichage (voir
 * [FinancialPlanProgress]).
 */
data class FinancialPlanUiItem(
    val plan: FinancialPlan,
    val totalPlanned: Long,
    val remainingAmount: Long,
    val progressPercent: Int,
    val isOverBudget: Boolean
)

/**
 * ViewModel de l'écran liste "Planification" : une planification par carte, avec disponible/
 * prévu/reste et une barre de progression (voir cahier des charges, sections 3/4).
 *
 * `combine(observePlans(), observeAllItems())` puis regroupement en Kotlin — même pattern que
 * [com.arzikina.ne.presentation.budget.BudgetViewModel] (voir sa doc pour le raisonnement : pas de
 * `combine(List<Flow>)` dynamique, un flux OBSERVANT TOUTES les dépenses prévues de l'utilisateur
 * une seule fois plutôt qu'un flux par planification).
 */
@HiltViewModel
class FinancialPlansViewModel @Inject constructor(
    private val financialPlanRepository: FinancialPlanRepository
) : ViewModel() {

    val uiState: StateFlow<AppResult<List<FinancialPlanUiItem>>> = combine(
        financialPlanRepository.observePlans(),
        financialPlanRepository.observeAllItems()
    ) { plans, allItems ->
        val itemsByPlanId = allItems.groupBy { it.planId }
        plans.map { plan ->
            val items = itemsByPlanId[plan.id].orEmpty()
            val totalPlanned = FinancialPlanProgress.calculateTotalPlanned(items)
            FinancialPlanUiItem(
                plan = plan,
                totalPlanned = totalPlanned,
                remainingAmount = FinancialPlanProgress.calculateRemainingAmount(plan.availableAmount, totalPlanned),
                progressPercent = FinancialPlanProgress.calculateProgress(plan.availableAmount, totalPlanned),
                isOverBudget = FinancialPlanProgress.calculateOverBudget(plan.availableAmount, totalPlanned)
            )
        }
    }
        .map<List<FinancialPlanUiItem>, AppResult<List<FinancialPlanUiItem>>> { AppResult.Success(it) }
        .catch { throwable -> emit(AppResult.Error(throwable.message ?: "Erreur inconnue", throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )

    fun deletePlan(id: Long) {
        viewModelScope.launch {
            financialPlanRepository.deletePlan(id)
        }
    }
}
