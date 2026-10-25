package com.naniger.arzikina.domain.shop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StockRulesTest {

    private fun validate(type: StockMovementType, delta: Long, reason: String? = null, unit: ProductUnit = ProductUnit.PIECE) =
        StockRules.validate(type, delta, reason, unit)

    @Test fun `un reapprovisionnement positif est valide`() {
        assertNull(validate(StockMovementType.RESTOCK, 5_000))
    }

    @Test fun `une quantite nulle est refusee`() {
        assertEquals(StockMovementError.ZERO_QUANTITY, validate(StockMovementType.RESTOCK, 0))
    }

    @Test fun `le sens du mouvement est impose par son type`() {
        assertEquals(StockMovementError.WRONG_DIRECTION, validate(StockMovementType.RESTOCK, -1_000))
        assertEquals(StockMovementError.WRONG_DIRECTION, validate(StockMovementType.SALE, 1_000))
        assertEquals(StockMovementError.WRONG_DIRECTION, validate(StockMovementType.SALE_CANCELLATION, -1_000))
        assertEquals(StockMovementError.WRONG_DIRECTION, validate(StockMovementType.CUSTOMER_RETURN, -1_000))
        assertNull(validate(StockMovementType.SALE, -1_000))
        assertNull(validate(StockMovementType.SALE_CANCELLATION, 1_000))
    }

    @Test fun `un ajustement accepte les deux sens mais exige un motif`() {
        assertNull(validate(StockMovementType.ADJUSTMENT, 2_000, "Inventaire"))
        assertNull(validate(StockMovementType.ADJUSTMENT, -2_000, "Inventaire"))
        assertEquals(StockMovementError.REASON_REQUIRED, validate(StockMovementType.ADJUSTMENT, 2_000))
        assertEquals(StockMovementError.REASON_REQUIRED, validate(StockMovementType.ADJUSTMENT, 2_000, "   "))
    }

    @Test fun `perte et casse exigent un motif`() {
        assertEquals(StockMovementError.REASON_REQUIRED, validate(StockMovementType.DAMAGED_OR_LOST, -1_000))
        assertNull(validate(StockMovementType.DAMAGED_OR_LOST, -1_000, "Bouteille cassee"))
    }

    @Test fun `une unite comptee refuse les fractions, une unite mesuree les accepte`() {
        assertEquals(StockMovementError.FRACTION_NOT_ALLOWED, validate(StockMovementType.RESTOCK, 1_500, unit = ProductUnit.PIECE))
        assertNull(validate(StockMovementType.RESTOCK, 1_500, unit = ProductUnit.KILOGRAM))
        assertNull(validate(StockMovementType.RESTOCK, 250, unit = ProductUnit.LITER))
    }

    @Test fun `les quantites hors plafond sont refusees`() {
        assertEquals(StockMovementError.QUANTITY_TOO_LARGE, validate(StockMovementType.RESTOCK, StockRules.MAX_QUANTITY_MILLI + 1_000))
        assertEquals(StockMovementError.QUANTITY_TOO_LARGE, validate(StockMovementType.ADJUSTMENT, Long.MIN_VALUE, "x"))
    }

    @Test fun `le stock est la somme des mouvements`() {
        assertEquals(0L, StockRules.stockOf(emptyList()))
        assertEquals(7_000L, StockRules.stockOf(listOf(10_000L, -2_000L, -1_000L)))
    }

    @Test fun `un debordement du stock n'est jamais silencieux`() {
        assertThrows(ArithmeticException::class.java) { StockRules.stockOf(listOf(Long.MAX_VALUE, 1L)) }
    }

    @Test fun `niveaux de stock`() {
        assertEquals(StockLevel.OUT_OF_STOCK, StockRules.levelOf(0, 5_000))
        assertEquals(StockLevel.OUT_OF_STOCK, StockRules.levelOf(-1_000, 0))
        assertEquals(StockLevel.LOW, StockRules.levelOf(5_000, 5_000))
        assertEquals(StockLevel.LOW, StockRules.levelOf(2_000, 5_000))
        assertEquals(StockLevel.NORMAL, StockRules.levelOf(6_000, 5_000))
        // Seuil 0 = aucune alerte de stock faible.
        assertEquals(StockLevel.NORMAL, StockRules.levelOf(1_000, 0))
    }

    @Test fun `la vente a decouvert est impossible`() {
        assertTrue(StockRules.canSell(3_000, 3_000))
        assertTrue(StockRules.canSell(3_000, 1_000))
        assertFalse(StockRules.canSell(3_000, 3_001))
        assertFalse(StockRules.canSell(0, 1_000))
        assertFalse(StockRules.canSell(3_000, 0))
        assertFalse(StockRules.canSell(3_000, -1_000))
    }

    @Test fun `un stock negatif ne permet aucune vente`() {
        assertFalse(StockRules.canSell(-1_000, 1_000))
    }

    @Test fun `les valeurs stockees inconnues retombent sur un defaut sur`() {
        assertEquals(ProductUnit.OTHER, ProductUnit.fromStored("FUTURE_UNIT"))
        assertEquals(ProductUnit.KILOGRAM, ProductUnit.fromStored("KILOGRAM"))
        assertEquals(StockMovementType.OTHER, StockMovementType.fromStored("FUTURE_TYPE"))
        assertEquals(StockMovementType.SALE, StockMovementType.fromStored("SALE"))
        assertEquals(SaleStatus.CONFIRMED, SaleStatus.fromStored("FUTURE_STATUS"))
        assertEquals(SaleStatus.CANCELLED, SaleStatus.fromStored("CANCELLED"))
    }
}
