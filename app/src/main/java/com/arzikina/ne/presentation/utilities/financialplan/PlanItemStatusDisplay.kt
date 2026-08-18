package com.arzikina.ne.presentation.utilities.financialplan

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.PlanItemStatus

/**
 * Libellé + couleur de la pastille d'état d'une [com.arzikina.ne.domain.model.FinancialPlanItem]
 * (voir cahier des charges "Planification financière", section 10) — même principe que
 * `loanStatusDisplay` (voir sa doc).
 *
 * [PlanItemStatus.CANCELLED] en gris neutre plutôt qu'en rouge (`expense_red`) : une dépense
 * annulée est un choix de l'utilisateur, pas une erreur ni une alerte — voir aussi
 * `FinancialPlanDetailAdapter.ItemViewHolder.bind`, qui atténue en plus toute la ligne (alpha) et
 * barre le nom pour cet état.
 */
data class PlanItemStatusDisplay(
    @StringRes val labelRes: Int,
    @ColorRes val colorRes: Int
)

fun planItemStatusDisplay(status: PlanItemStatus): PlanItemStatusDisplay = when (status) {
    PlanItemStatus.TO_PLAN -> PlanItemStatusDisplay(R.string.plan_item_status_to_plan, R.color.arzikina_outline)
    PlanItemStatus.DONE -> PlanItemStatusDisplay(R.string.plan_item_status_done, R.color.income_green)
    PlanItemStatus.CANCELLED -> PlanItemStatusDisplay(R.string.plan_item_status_cancelled, R.color.arzikina_on_surface_variant)
}
