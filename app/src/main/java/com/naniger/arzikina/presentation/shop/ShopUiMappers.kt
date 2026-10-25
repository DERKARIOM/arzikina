package com.naniger.arzikina.presentation.shop

import android.content.Context
import android.content.res.ColorStateList
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.shop.ProductQuantityFormat
import com.naniger.arzikina.domain.shop.ProductUnit
import com.naniger.arzikina.domain.shop.StockLevel
import com.naniger.arzikina.domain.shop.StockMovementType
import com.naniger.arzikina.util.Money

/** Libellés et couleurs du module Boutique : seul endroit qui relie le domaine aux ressources. */

@StringRes
fun ProductUnit.labelRes(): Int = when (this) {
    ProductUnit.PIECE -> R.string.shop_unit_piece
    ProductUnit.PACK -> R.string.shop_unit_pack
    ProductUnit.CARTON -> R.string.shop_unit_carton
    ProductUnit.KILOGRAM -> R.string.shop_unit_kilogram
    ProductUnit.LITER -> R.string.shop_unit_liter
    ProductUnit.OTHER -> R.string.shop_unit_other
}

@StringRes
fun ProductUnit.shortLabelRes(): Int = when (this) {
    ProductUnit.PIECE -> R.string.shop_unit_piece_short
    ProductUnit.PACK -> R.string.shop_unit_pack_short
    ProductUnit.CARTON -> R.string.shop_unit_carton_short
    ProductUnit.KILOGRAM -> R.string.shop_unit_kilogram_short
    ProductUnit.LITER -> R.string.shop_unit_liter_short
    ProductUnit.OTHER -> R.string.shop_unit_other_short
}

@StringRes
fun StockLevel.labelRes(): Int = when (this) {
    StockLevel.NORMAL -> R.string.shop_stock_level_normal
    StockLevel.LOW -> R.string.shop_stock_level_low
    StockLevel.OUT_OF_STOCK -> R.string.shop_stock_level_out
}

@ColorRes
fun StockLevel.colorRes(): Int = when (this) {
    StockLevel.NORMAL -> R.color.shop_stock_normal
    StockLevel.LOW -> R.color.shop_stock_low
    StockLevel.OUT_OF_STOCK -> R.color.shop_stock_out
}

@StringRes
fun StockMovementType.labelRes(): Int = when (this) {
    StockMovementType.INITIAL -> R.string.shop_stock_type_initial
    StockMovementType.RESTOCK -> R.string.shop_stock_type_restock
    StockMovementType.SALE -> R.string.shop_stock_type_sale
    StockMovementType.CUSTOMER_RETURN -> R.string.shop_stock_type_return
    StockMovementType.DAMAGED_OR_LOST -> R.string.shop_stock_type_damaged
    StockMovementType.ADJUSTMENT -> R.string.shop_stock_type_adjustment
    StockMovementType.SALE_CANCELLATION -> R.string.shop_stock_type_cancellation
    StockMovementType.OTHER -> R.string.shop_stock_type_other
}

/** Pastille d'état : « Archivé » (gris) l'emporte sur le niveau de stock. */
fun TextView.showStockStatus(level: StockLevel, archived: Boolean) {
    setText(if (archived) R.string.shop_product_archived else level.labelRes())
    val color = if (archived) R.color.shop_stock_archived else level.colorRes()
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, color))
}

/** Formatage d'affichage (montants et quantités), commun à tous les écrans du module. */
object ShopFormat {

    fun money(minor: Long, currencyCode: String): String =
        "${Money.formatAmount(minor)} ${Money.symbolOf(currencyCode)}"

    fun quantity(context: Context, quantityMilli: Long, unit: ProductUnit): String =
        "${ProductQuantityFormat.format(quantityMilli)} ${context.getString(unit.shortLabelRes())}"
}
