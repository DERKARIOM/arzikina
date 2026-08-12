package com.arzikina.ne.presentation.utilities.recurring

import androidx.annotation.StringRes
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.RecurringFrequency

/** Libellé affiché d'une [RecurringFrequency] (voir `item_recurring_summary_header.xml`
 * et le formulaire de règle récurrente). */
@StringRes
fun RecurringFrequency.labelRes(): Int = when (this) {
    RecurringFrequency.ONCE -> R.string.recurring_frequency_once
    RecurringFrequency.DAILY -> R.string.recurring_frequency_daily
    RecurringFrequency.WEEKLY -> R.string.recurring_frequency_weekly
    RecurringFrequency.BIWEEKLY -> R.string.recurring_frequency_biweekly
    RecurringFrequency.MONTHLY -> R.string.recurring_frequency_monthly
    RecurringFrequency.QUARTERLY -> R.string.recurring_frequency_quarterly
    RecurringFrequency.SEMIANNUAL -> R.string.recurring_frequency_semiannual
    RecurringFrequency.YEARLY -> R.string.recurring_frequency_yearly
}
