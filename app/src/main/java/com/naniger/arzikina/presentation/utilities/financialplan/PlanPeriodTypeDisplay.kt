package com.naniger.arzikina.presentation.utilities.financialplan

import androidx.annotation.StringRes
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.PlanPeriodType

/** Libellé affiché d'un [PlanPeriodType] (voir `FinancialPlanFormFragment.setUpPeriodTypeDropdown`,
 * Étape 8) — même principe que `RecurringFrequencyDisplay.labelRes()`. */
@StringRes
fun PlanPeriodType.labelRes(): Int = when (this) {
    PlanPeriodType.NONE -> R.string.plan_period_none
    PlanPeriodType.MONTHLY -> R.string.plan_period_monthly
    PlanPeriodType.YEARLY -> R.string.plan_period_yearly
    PlanPeriodType.CUSTOM -> R.string.plan_period_custom
}
