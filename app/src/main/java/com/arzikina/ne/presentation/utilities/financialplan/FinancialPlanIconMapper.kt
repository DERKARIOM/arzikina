package com.arzikina.ne.presentation.utilities.financialplan

import androidx.annotation.DrawableRes
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.FinancialPlanIcon

/**
 * Mapping [FinancialPlanIcon] → drawable concret — voir `AccountIconMapper`/`CategoryIconMapper`
 * pour le même principe (le domaine ne dépend jamais d'Android).
 *
 * Drawables dédiés (`ic_financial_plan_*`), jamais partagés avec `AccountIcon`/`CategoryIcon`
 * même quand le concept se ressemble (ex. [FinancialPlanIcon.WALLET]/[FinancialPlanIcon.OTHER]) —
 * même convention que partout ailleurs dans le projet (chaque écran a ses propres drawables).
 */
object FinancialPlanIconMapper {
    @DrawableRes
    fun iconFor(icon: FinancialPlanIcon): Int = when (icon) {
        FinancialPlanIcon.WALLET -> R.drawable.ic_financial_plan_wallet_24
        FinancialPlanIcon.RING -> R.drawable.ic_financial_plan_ring_24
        FinancialPlanIcon.MOTORCYCLE -> R.drawable.ic_financial_plan_motorcycle_24
        FinancialPlanIcon.PLANE -> R.drawable.ic_financial_plan_plane_24
        FinancialPlanIcon.HOME -> R.drawable.ic_financial_plan_home_24
        FinancialPlanIcon.GRADUATION -> R.drawable.ic_financial_plan_graduation_24
        FinancialPlanIcon.CELEBRATION -> R.drawable.ic_financial_plan_celebration_24
        FinancialPlanIcon.OTHER -> R.drawable.ic_financial_plan_other_24
    }
}
