package com.naniger.arzikina.presentation.shop.form

import com.naniger.arzikina.domain.shop.ProductDraft
import com.naniger.arzikina.domain.shop.ProductError
import com.naniger.arzikina.domain.shop.ProductQuantityFormat
import com.naniger.arzikina.domain.shop.ProductUnit
import com.naniger.arzikina.domain.shop.ProductValidator
import com.naniger.arzikina.util.Money

/** Saisie brute du formulaire produit (textes tels que tapés par l'utilisateur). */
data class ProductFormInput(
    val id: Long = 0L,
    val name: String = "",
    val description: String = "",
    val sku: String = "",
    val barcode: String = "",
    val categoryId: Long? = null,
    val unit: ProductUnit = ProductUnit.PIECE,
    val purchasePrice: String = "",
    val salePrice: String = "",
    val initialStock: String = "",
    val minStock: String = "",
    val expiryDate: Long? = null
)

sealed interface ProductFormParseResult {
    data class Valid(val draft: ProductDraft) : ProductFormParseResult
    data class Invalid(val errors: Set<ProductError>) : ProductFormParseResult
}

/**
 * Transforme la saisie brute en [ProductDraft] ou en liste d'erreurs de champ. Logique pure et
 * testable : les nombres illisibles sont signalés ici, les règles métier restent dans [ProductValidator]
 * (aucune règle dupliquée).
 */
object ProductFormParser {

    /** Au-delà, la valeur dépasserait de toute façon les plafonds métier (et risquerait de déborder un [Long]). */
    private const val MAX_NUMBER_LENGTH = 15

    fun parse(input: ProductFormInput): ProductFormParseResult {
        val errors = mutableSetOf<ProductError>()

        val sale = parseMoney(input.salePrice) ?: 0L.also { errors += ProductError.SALE_PRICE_INVALID }
        val purchase = input.purchasePrice.takeIf { it.isNotBlank() }?.let { text ->
            parseMoney(text).also { if (it == null) errors += ProductError.PURCHASE_PRICE_INVALID }
        }
        val min = parseOptionalQuantity(input.minStock) { errors += ProductError.MIN_STOCK_INVALID }
        // Le stock initial n'existe qu'à la création : ignoré (donc jamais en erreur) en modification.
        val initial = if (input.id == 0L) {
            parseOptionalQuantity(input.initialStock) { errors += ProductError.INITIAL_STOCK_INVALID }
        } else {
            0L
        }

        val draft = ProductDraft(
            id = input.id,
            name = input.name,
            description = input.description.takeIf { it.isNotBlank() },
            sku = input.sku.takeIf { it.isNotBlank() },
            barcode = input.barcode.takeIf { it.isNotBlank() },
            categoryId = input.categoryId,
            unit = input.unit,
            purchasePriceMinor = purchase,
            salePriceMinor = sale,
            minStockMilli = min,
            initialStockMilli = initial,
            expiryDate = input.expiryDate
        )
        errors += ProductValidator.validate(draft)
        return if (errors.isEmpty()) ProductFormParseResult.Valid(draft) else ProductFormParseResult.Invalid(errors)
    }

    private fun parseMoney(text: String): Long? {
        val significant = text.filterNot { it.isWhitespace() || Character.isSpaceChar(it) }
        if (significant.length > MAX_NUMBER_LENGTH) return null
        return Money.parseToMinorUnits(significant)
    }

    /** Vide = 0 ; illisible = 0 et [onInvalid] est appelé. */
    private inline fun parseOptionalQuantity(text: String, onInvalid: () -> Unit): Long {
        if (text.isBlank()) return 0L
        if (text.length > MAX_NUMBER_LENGTH) return 0L.also { onInvalid() }
        return ProductQuantityFormat.parse(text) ?: 0L.also { onInvalid() }
    }
}
