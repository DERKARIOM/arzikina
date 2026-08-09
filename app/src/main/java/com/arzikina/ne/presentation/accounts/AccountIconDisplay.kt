package com.arzikina.ne.presentation.accounts

import androidx.annotation.StringRes
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.AccountIcon

/**
 * Libellé affiché sous le nom du compte sur sa carte (voir maquette
 * "RÉORGANISATION – PAGE COMPTE" : "Others"/"Banque"/"Cash").
 *
 * Historique : avant l'introduction de [com.arzikina.ne.domain.model.AccountType],
 * cette fonction dérivait le "type" affiché directement de [AccountIcon], qui
 * jouait alors ce rôle par convention. Ce n'est plus le cas — [AccountType]
 * porte maintenant le type — mais cette fonction reste utile telle quelle
 * comme libellé de l'ICÔNE elle-même (ex. dans un futur sélecteur avec noms),
 * d'où sa conservation à l'identique plutôt qu'une suppression.
 */
@StringRes
fun AccountIcon.displayTextRes(): Int = when (this) {
    AccountIcon.CASH -> R.string.account_type_cash
    AccountIcon.BANK -> R.string.account_type_bank
    AccountIcon.MOBILE_MONEY -> R.string.account_type_mobile_money
    AccountIcon.SAVINGS -> R.string.account_type_savings
    AccountIcon.WALLET -> R.string.account_type_wallet
    AccountIcon.CREDIT_CARD -> R.string.account_type_credit_card
    AccountIcon.OTHER -> R.string.account_type_other
}
