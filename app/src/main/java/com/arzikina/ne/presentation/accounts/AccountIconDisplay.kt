package com.arzikina.ne.presentation.accounts

import androidx.annotation.StringRes
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.AccountIcon

/**
 * Libellé affiché comme "type" de compte sur sa carte (voir maquette
 * "RÉORGANISATION – PAGE COMPTE" : "Others"/"Banque"/"Cash" sous le nom du
 * compte) — dérivé de [AccountIcon] plutôt qu'un nouveau champ en base : cette
 * icône EST déjà, fonctionnellement, le type choisi à la création du compte.
 * Même raisonnement que [com.arzikina.ne.presentation.transactions.PaymentMethodDisplay].
 */
@StringRes
fun AccountIcon.displayTextRes(): Int = when (this) {
    AccountIcon.CASH -> R.string.account_type_cash
    AccountIcon.BANK -> R.string.account_type_bank
    AccountIcon.MOBILE_MONEY -> R.string.account_type_mobile_money
    AccountIcon.SAVINGS -> R.string.account_type_savings
    AccountIcon.WALLET -> R.string.account_type_wallet
    AccountIcon.OTHER -> R.string.account_type_other
}
