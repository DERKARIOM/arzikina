package com.naniger.arzikina.presentation.transactions

import androidx.annotation.StringRes
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.FeeType

/**
 * Seul endroit de l'app qui associe un [FeeType] (clé de domaine, sans texte) à sa chaîne
 * localisée — même raisonnement/pattern que [PaymentMethodDisplay]. Utilisé par le formulaire de
 * transaction (liste déroulante "Type de frais").
 */
@StringRes
fun FeeType.displayTextRes(): Int = when (this) {
    FeeType.TRANSFER -> R.string.fee_type_transfer
    FeeType.BANK -> R.string.fee_type_bank
    FeeType.COMMISSION -> R.string.fee_type_commission
    FeeType.SERVICE -> R.string.fee_type_service
    FeeType.OTHER -> R.string.fee_type_other
}
