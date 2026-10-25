package com.naniger.arzikina.presentation.shop.form

import com.naniger.arzikina.domain.shop.ProductError
import com.naniger.arzikina.domain.shop.ProductUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductFormParserTest {

    private fun input(
        name: String = "Sucre 1 kg",
        sale: String = "1 200",
        purchase: String = "",
        initial: String = "",
        min: String = "",
        unit: ProductUnit = ProductUnit.PIECE,
        id: Long = 0L
    ) = ProductFormInput(id = id, name = name, salePrice = sale, purchasePrice = purchase, initialStock = initial, minStock = min, unit = unit)

    private fun valid(i: ProductFormInput) = (ProductFormParser.parse(i) as ProductFormParseResult.Valid).draft
    private fun errors(i: ProductFormInput) = (ProductFormParser.parse(i) as ProductFormParseResult.Invalid).errors

    @Test fun `saisie minimale valide`() {
        val draft = valid(input())
        assertEquals("Sucre 1 kg", draft.name)
        assertEquals(120_000L, draft.salePriceMinor)
        assertNull(draft.purchasePriceMinor)
        assertEquals(0L, draft.minStockMilli)
        assertEquals(0L, draft.initialStockMilli)
    }

    @Test fun `prix et quantites sont convertis en unites mineures`() {
        val draft = valid(input(purchase = "900,50", initial = "24", min = "5"))
        assertEquals(90_050L, draft.purchasePriceMinor)
        assertEquals(24_000L, draft.initialStockMilli)
        assertEquals(5_000L, draft.minStockMilli)
    }

    @Test fun `un prix d'achat vide reste inconnu et non zero`() {
        assertNull(valid(input(purchase = "  ")).purchasePriceMinor)
    }

    @Test fun `un prix de vente vide ou illisible est signale`() {
        assertEquals(setOf(ProductError.SALE_PRICE_INVALID), errors(input(sale = "")))
        assertEquals(setOf(ProductError.SALE_PRICE_INVALID), errors(input(sale = "abc")))
        assertEquals(setOf(ProductError.SALE_PRICE_INVALID), errors(input(sale = "0")))
    }

    @Test fun `un prix d'achat illisible est signale`() {
        assertEquals(setOf(ProductError.PURCHASE_PRICE_INVALID), errors(input(purchase = "x")))
    }

    @Test fun `un nombre demesure est refuse sans debordement`() {
        assertEquals(setOf(ProductError.SALE_PRICE_INVALID), errors(input(sale = "9".repeat(30))))
    }

    @Test fun `les quantites illisibles sont signalees`() {
        val e = errors(input(initial = "1,2,3", min = "-4"))
        assertEquals(setOf(ProductError.INITIAL_STOCK_INVALID, ProductError.MIN_STOCK_INVALID), e)
    }

    @Test fun `quantite decimale refusee pour une unite comptee et acceptee pour le poids`() {
        assertTrue(ProductError.INITIAL_STOCK_INVALID in errors(input(initial = "2,5")))
        assertEquals(2_500L, valid(input(initial = "2,5", unit = ProductUnit.KILOGRAM)).initialStockMilli)
    }

    @Test fun `en modification le stock initial est ignore meme illisible`() {
        val draft = valid(input(id = 9L, initial = "n'importe quoi"))
        assertEquals(0L, draft.initialStockMilli)
    }

    @Test fun `les textes facultatifs vides deviennent null`() {
        val draft = valid(input())
        assertNull(draft.sku)
        assertNull(draft.barcode)
        assertNull(draft.description)
    }

    @Test fun `plusieurs erreurs sont cumulees sans doublon`() {
        val e = errors(input(name = "", sale = ""))
        assertEquals(setOf(ProductError.NAME_REQUIRED, ProductError.SALE_PRICE_INVALID), e)
    }
}
