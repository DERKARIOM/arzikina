package com.naniger.arzikina.presentation.shop.sales

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.PaymentMethod
import com.naniger.arzikina.domain.shop.SaleStatus

/** Libellés et couleurs d'affichage des ventes, communs à l'historique et au détail. */
@StringRes
fun SaleStatus.labelRes(): Int = when (this) {
    SaleStatus.CONFIRMED -> R.string.shop_sale_status_confirmed
    SaleStatus.CANCELLED -> R.string.shop_sale_status_cancelled
}

@StringRes
fun PaymentMethod.labelRes(): Int = when (this) {
    PaymentMethod.CASH -> R.string.payment_method_cash
    PaymentMethod.CARD -> R.string.payment_method_card
    PaymentMethod.MOBILE_MONEY -> R.string.payment_method_mobile_money
    PaymentMethod.BANK_TRANSFER -> R.string.payment_method_bank_transfer
    PaymentMethod.OTHER -> R.string.payment_method_other
}

/** Pastille de statut : verte pour une vente validée, rouge pour une vente annulée. */
fun TextView.showSaleStatus(status: SaleStatus) {
    setText(status.labelRes())
    val color = if (status == SaleStatus.CANCELLED) R.color.shop_stock_out else R.color.shop_stock_normal
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, color))
}
