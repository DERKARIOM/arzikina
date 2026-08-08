package com.arzikina.ne.presentation.transactions

import androidx.annotation.StringRes
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.PaymentMethod

/**
 * Seul endroit de l'app qui associe un [PaymentMethod] (clé de domaine, sans
 * texte) à sa chaîne localisée — même raisonnement que
 * [com.arzikina.ne.presentation.auth.SecurityQuestionDisplay]. Utilisé par le
 * formulaire de transaction (liste déroulante) et par [TransactionItemBinder]
 * (affichage dans les listes).
 */
@StringRes
fun PaymentMethod.displayTextRes(): Int = when (this) {
    PaymentMethod.CASH -> R.string.payment_method_cash
    PaymentMethod.CARD -> R.string.payment_method_card
    PaymentMethod.MOBILE_MONEY -> R.string.payment_method_mobile_money
    PaymentMethod.BANK_TRANSFER -> R.string.payment_method_bank_transfer
    PaymentMethod.OTHER -> R.string.payment_method_other
}
