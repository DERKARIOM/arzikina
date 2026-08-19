package com.arzikina.ne.presentation.utilities.financialplan

import com.arzikina.ne.domain.model.FinancialPlan
import com.arzikina.ne.domain.model.FinancialPlanItem
import com.arzikina.ne.util.FinancialPlanProgress

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
 * Regroupe [allItems] par planification et calcule la progression de chacune (voir
 * [FinancialPlanProgress]) — extrait de [FinancialPlansViewModel] pour être réutilisé tel quel par
 * [com.arzikina.ne.presentation.accounts.AccountsViewModel] (onglet "Planification" de "Mes
 * comptes") : même calcul affiché aux deux endroits, une seule fois écrit (voir instructions
 * projet : "évite absolument le code dupliqué").
 *
 * Aucun filtre ni tri ici (ex. [com.arzikina.ne.domain.model.PlanStatus], limite d'affichage) :
 * ces préoccupations restent propres à chaque appelant, comme c'était déjà le cas quand ce calcul
 * vivait séparément dans `DashboardViewModel.featuredFinancialPlans` (retiré depuis) et
 * [FinancialPlansViewModel.uiState].
 */
fun buildFinancialPlanUiItems(
    plans: List<FinancialPlan>,
    allItems: List<FinancialPlanItem>
): List<FinancialPlanUiItem> {
    val itemsByPlanId = allItems.groupBy { it.planId }
    return plans.map { plan ->
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
