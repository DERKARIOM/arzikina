package com.arzikina.ne.presentation.utilities.financialplan

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.PlanItemPriority

/**
 * Libellé + couleur de l'indicateur de priorité d'une
 * [com.arzikina.ne.domain.model.FinancialPlanItem] — convention courante (rouge = urgent, ambre =
 * intermédiaire, gris = faible), même principe que [planItemStatusDisplay].
 */
data class PlanItemPriorityDisplay(
    @StringRes val labelRes: Int,
    @ColorRes val colorRes: Int
)

fun planItemPriorityDisplay(priority: PlanItemPriority): PlanItemPriorityDisplay = when (priority) {
    PlanItemPriority.ESSENTIAL -> PlanItemPriorityDisplay(R.string.plan_item_priority_essential, R.color.expense_red)
    PlanItemPriority.IMPORTANT -> PlanItemPriorityDisplay(R.string.plan_item_priority_important, R.color.warning_amber)
    PlanItemPriority.OPTIONAL -> PlanItemPriorityDisplay(R.string.plan_item_priority_optional, R.color.arzikina_outline)
}
