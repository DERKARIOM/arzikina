package com.arzikina.ne.presentation.accounts

import androidx.annotation.DrawableRes
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.AccountIcon

/**
 * Correspondance [AccountIcon] -> ressource Vector Drawable concrète.
 * Voir [com.arzikina.ne.presentation.categories.CategoryIconMapper] pour le
 * même raisonnement (le domaine ne connaît jamais de référence Android).
 */
object AccountIconMapper {

    @DrawableRes
    fun iconFor(icon: AccountIcon): Int = when (icon) {
        AccountIcon.CASH -> R.drawable.ic_account_cash_24
        AccountIcon.BANK -> R.drawable.ic_account_bank_24
        AccountIcon.MOBILE_MONEY -> R.drawable.ic_account_mobile_money_24
        AccountIcon.SAVINGS -> R.drawable.ic_account_savings_24
        AccountIcon.WALLET -> R.drawable.ic_account_wallet_24
        AccountIcon.CREDIT_CARD -> R.drawable.ic_account_credit_card_24
        AccountIcon.OTHER -> R.drawable.ic_account_other_24
    }
}
