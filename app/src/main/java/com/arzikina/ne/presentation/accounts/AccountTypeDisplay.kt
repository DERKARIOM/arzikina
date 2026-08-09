package com.arzikina.ne.presentation.accounts

import androidx.annotation.StringRes
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.AccountType

/**
 * Libellé du menu déroulant "Type de compte" (voir `AccountFormFragment.setUpTypeDropdown`).
 * Réutilise volontairement les MÊMES chaînes que [AccountIcon.displayTextRes] (`account_type_*`) :
 * avant l'introduction de [AccountType], ces chaînes désignaient déjà exactement ces types via
 * l'icône — les dupliquer n'apporterait rien.
 */
@StringRes
fun AccountType.displayTextRes(): Int = when (this) {
    AccountType.CASH -> R.string.account_type_cash
    AccountType.BANK -> R.string.account_type_bank
    AccountType.MOBILE_MONEY -> R.string.account_type_mobile_money
    AccountType.SAVINGS -> R.string.account_type_savings
    AccountType.CREDIT_CARD -> R.string.account_type_credit_card
}
