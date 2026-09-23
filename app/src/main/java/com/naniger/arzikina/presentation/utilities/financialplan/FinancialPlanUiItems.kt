package com.naniger.arzikina.presentation.utilities.financialplan

import com.naniger.arzikina.domain.model.FinancialPlan
import com.naniger.arzikina.domain.model.FinancialPlanItem
import com.naniger.arzikina.domain.model.PlanStatus
import com.naniger.arzikina.util.FinancialPlanProgress

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
 * [com.naniger.arzikina.presentation.accounts.AccountsViewModel] (onglet "Planification" de "Mes
 * comptes") : même calcul affiché aux deux endroits, une seule fois écrit (voir instructions
 * projet : "évite absolument le code dupliqué").
 *
 * Exclut les planifications [PlanStatus.ARCHIVED] : les DEUX appelants ci-dessus affichent une
 * LISTE de planifications, où une planification archivée (statut saisi par l'utilisateur, pas
 * encore d'UI d'archivage côté Android — seulement depuis Arsikina Web pour l'instant, voir
 * `arzikina-web-sync/src/routes/planifications.tsx`) ne doit jamais apparaître ni compter comme
 * active, même règle que côté Web (voir `services/finance.ts` côté web, section PlanningService).
 * Placé ici plutôt que dans chacun des deux appelants pour ne pas le dupliquer, puisqu'il est
 * strictement identique aux deux endroits. Ne PAS l'ajouter à
 * [FinancialPlanDetailViewModel.uiState] : cet écran résout une planification par id directement
 * sur `observePlans()` (sans passer par cette fonction) précisément pour rester accessible même
 * archivée.
 */
fun buildFinancialPlanUiItems(
    plans: List<FinancialPlan>,
    allItems: List<FinancialPlanItem>
): List<FinancialPlanUiItem> {
    val itemsByPlanId = allItems.groupBy { it.planId }
    return plans.filter { it.status != PlanStatus.ARCHIVED }.map { plan ->
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
