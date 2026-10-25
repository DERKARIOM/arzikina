package com.naniger.arzikina.domain.shop

import com.naniger.arzikina.domain.issuedreceipt.ReceiptCalculator

/** Erreur de saisie d'un produit (voir [ProductValidator]). */
enum class ProductError {
    NAME_REQUIRED,
    NAME_TOO_LONG,
    DESCRIPTION_TOO_LONG,
    SKU_TOO_LONG,
    BARCODE_TOO_LONG,
    SALE_PRICE_INVALID,
    PURCHASE_PRICE_INVALID,
    MIN_STOCK_INVALID,
    INITIAL_STOCK_INVALID
}

/**
 * Validation PURE d'un [ProductDraft], appelée par le repository avant toute écriture (la couche
 * présentation l'utilise aussi pour signaler les champs en erreur).
 *
 * Règles : nom obligatoire ; prix de vente strictement positif ; prix d'achat positif ou nul quand il
 * est renseigné ; seuil minimal positif ou nul, entier pour une unité comptée ; stock initial positif
 * ou nul, entier pour une unité comptée (voir [StockRules.validate]) — le stock initial n'est contrôlé
 * qu'à la création, il est ignoré en modification.
 */
object ProductValidator {

    const val MAX_NAME_LENGTH = 191
    const val MAX_DESCRIPTION_LENGTH = 2_000
    const val MAX_CODE_LENGTH = 64

    fun validate(draft: ProductDraft): List<ProductError> {
        val errors = mutableListOf<ProductError>()
        val name = draft.name.trim()
        if (name.isEmpty()) errors += ProductError.NAME_REQUIRED
        else if (name.length > MAX_NAME_LENGTH) errors += ProductError.NAME_TOO_LONG
        if ((draft.description?.length ?: 0) > MAX_DESCRIPTION_LENGTH) errors += ProductError.DESCRIPTION_TOO_LONG
        if ((draft.sku?.trim()?.length ?: 0) > MAX_CODE_LENGTH) errors += ProductError.SKU_TOO_LONG
        if ((draft.barcode?.trim()?.length ?: 0) > MAX_CODE_LENGTH) errors += ProductError.BARCODE_TOO_LONG
        if (draft.salePriceMinor <= 0L || draft.salePriceMinor > ReceiptCalculator.MAX_AMOUNT_MINOR) {
            errors += ProductError.SALE_PRICE_INVALID
        }
        draft.purchasePriceMinor?.let {
            if (it < 0L || it > ReceiptCalculator.MAX_AMOUNT_MINOR) errors += ProductError.PURCHASE_PRICE_INVALID
        }
        if (!isValidQuantity(draft.minStockMilli, draft.unit)) errors += ProductError.MIN_STOCK_INVALID
        if (draft.id == 0L && draft.initialStockMilli != 0L &&
            StockRules.validate(StockMovementType.INITIAL, draft.initialStockMilli, null, draft.unit) != null
        ) {
            errors += ProductError.INITIAL_STOCK_INVALID
        }
        return errors
    }

    /** Quantité positive ou nulle, plafonnée, entière si l'unité est comptée. */
    private fun isValidQuantity(quantityMilli: Long, unit: ProductUnit): Boolean =
        quantityMilli in 0..StockRules.MAX_QUANTITY_MILLI &&
            (unit.allowsFraction || quantityMilli % StockRules.QUANTITY_UNIT_MILLI == 0L)
}
