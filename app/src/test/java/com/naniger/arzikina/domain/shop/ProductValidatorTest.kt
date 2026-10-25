package com.naniger.arzikina.domain.shop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductValidatorTest {

    private fun draft(
        name: String = "Sucre",
        sale: Long = 150_000L,
        purchase: Long? = null,
        min: Long = 0L,
        initial: Long = 0L,
        unit: ProductUnit = ProductUnit.PIECE,
        id: Long = 0L
    ) = ProductDraft(
        id = id,
        name = name,
        salePriceMinor = sale,
        purchasePriceMinor = purchase,
        minStockMilli = min,
        initialStockMilli = initial,
        unit = unit
    )

    @Test fun `un produit valide ne produit aucune erreur`() {
        assertTrue(ProductValidator.validate(draft(purchase = 100_000L, min = 5_000L, initial = 24_000L)).isEmpty())
    }

    @Test fun `le nom est obligatoire`() {
        assertEquals(listOf(ProductError.NAME_REQUIRED), ProductValidator.validate(draft(name = "   ")))
    }

    @Test fun `un nom trop long est refuse`() {
        val errors = ProductValidator.validate(draft(name = "a".repeat(ProductValidator.MAX_NAME_LENGTH + 1)))
        assertEquals(listOf(ProductError.NAME_TOO_LONG), errors)
    }

    @Test fun `le prix de vente doit etre strictement positif`() {
        assertEquals(listOf(ProductError.SALE_PRICE_INVALID), ProductValidator.validate(draft(sale = 0L)))
        assertEquals(listOf(ProductError.SALE_PRICE_INVALID), ProductValidator.validate(draft(sale = -5L)))
    }

    @Test fun `le prix d'achat peut etre nul mais pas negatif`() {
        assertTrue(ProductValidator.validate(draft(purchase = 0L)).isEmpty())
        assertEquals(listOf(ProductError.PURCHASE_PRICE_INVALID), ProductValidator.validate(draft(purchase = -1L)))
    }

    @Test fun `une unite comptee refuse les quantites decimales`() {
        val errors = ProductValidator.validate(draft(min = 1_500L, initial = 2_500L))
        assertTrue(ProductError.MIN_STOCK_INVALID in errors)
        assertTrue(ProductError.INITIAL_STOCK_INVALID in errors)
    }

    @Test fun `une unite mesuree accepte les quantites decimales`() {
        assertTrue(ProductValidator.validate(draft(unit = ProductUnit.KILOGRAM, min = 500L, initial = 2_750L)).isEmpty())
    }

    @Test fun `le stock initial est ignore en modification`() {
        assertTrue(ProductValidator.validate(draft(id = 7L, initial = 2_500L)).isEmpty())
    }

    @Test fun `un seuil negatif est refuse`() {
        assertEquals(listOf(ProductError.MIN_STOCK_INVALID), ProductValidator.validate(draft(min = -1_000L)))
    }

    @Test fun `un stock initial negatif est refuse`() {
        assertEquals(listOf(ProductError.INITIAL_STOCK_INVALID), ProductValidator.validate(draft(initial = -1_000L)))
    }

    @Test fun `les codes trop longs sont refuses`() {
        val long = "x".repeat(ProductValidator.MAX_CODE_LENGTH + 1)
        val errors = ProductValidator.validate(draft().copy(sku = long, barcode = long))
        assertTrue(ProductError.SKU_TOO_LONG in errors)
        assertTrue(ProductError.BARCODE_TOO_LONG in errors)
    }
}
