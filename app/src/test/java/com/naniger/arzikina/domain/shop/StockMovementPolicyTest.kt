package com.naniger.arzikina.domain.shop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StockMovementPolicyTest {

    private fun plan(
        type: StockMovementType,
        delta: Long,
        current: Long = 10_000L,
        reason: String? = null,
        cost: Long? = null,
        unit: ProductUnit = ProductUnit.PIECE
    ) = StockMovementPolicy.plan(type, delta, reason, cost, unit, current)

    private fun rejected(p: StockChangePlan) = (p as StockChangePlan.Rejected).error
    private fun accepted(p: StockChangePlan) = (p as StockChangePlan.Accepted).newStockMilli

    @Test fun `un reapprovisionnement augmente le stock`() {
        assertEquals(15_000L, accepted(plan(StockMovementType.RESTOCK, 5_000L)))
    }

    @Test fun `un retour client augmente le stock`() {
        assertEquals(11_000L, accepted(plan(StockMovementType.CUSTOMER_RETURN, 1_000L)))
    }

    @Test fun `une perte diminue le stock et exige un motif`() {
        assertEquals(StockChangeError.REASON_REQUIRED, rejected(plan(StockMovementType.DAMAGED_OR_LOST, -2_000L)))
        assertEquals(8_000L, accepted(plan(StockMovementType.DAMAGED_OR_LOST, -2_000L, reason = "Périmé")))
    }

    @Test fun `une perte ne peut pas depasser le stock`() {
        assertEquals(
            StockChangeError.INSUFFICIENT_STOCK,
            rejected(plan(StockMovementType.DAMAGED_OR_LOST, -9_000L, current = 8_000L, reason = "Casse"))
        )
    }

    @Test fun `sortir exactement tout le stock est permis`() {
        assertEquals(0L, accepted(plan(StockMovementType.DAMAGED_OR_LOST, -10_000L, reason = "Vol")))
    }

    @Test fun `le sens du mouvement est controle`() {
        assertEquals(StockChangeError.WRONG_DIRECTION, rejected(plan(StockMovementType.RESTOCK, -1_000L)))
        assertEquals(StockChangeError.WRONG_DIRECTION, rejected(plan(StockMovementType.DAMAGED_OR_LOST, 1_000L, reason = "x")))
    }

    @Test fun `une quantite nulle est refusee`() {
        assertEquals(StockChangeError.ZERO_QUANTITY, rejected(plan(StockMovementType.RESTOCK, 0L)))
    }

    @Test fun `la correction accepte les deux sens avec motif`() {
        assertEquals(12_000L, accepted(plan(StockMovementType.ADJUSTMENT, 2_000L, reason = "Inventaire")))
        assertEquals(7_000L, accepted(plan(StockMovementType.ADJUSTMENT, -3_000L, reason = "Inventaire")))
        assertEquals(StockChangeError.REASON_REQUIRED, rejected(plan(StockMovementType.ADJUSTMENT, 2_000L)))
    }

    @Test fun `une unite comptee refuse les fractions`() {
        assertEquals(StockChangeError.FRACTION_NOT_ALLOWED, rejected(plan(StockMovementType.RESTOCK, 1_500L)))
        assertEquals(11_500L, accepted(plan(StockMovementType.RESTOCK, 1_500L, unit = ProductUnit.KILOGRAM)))
    }

    @Test fun `les types reserves aux autres flux sont refuses`() {
        listOf(StockMovementType.SALE, StockMovementType.SALE_CANCELLATION, StockMovementType.INITIAL, StockMovementType.OTHER)
            .forEach { assertEquals(StockChangeError.TYPE_NOT_ALLOWED, rejected(plan(it, 1_000L, reason = "x"))) }
    }

    @Test fun `le cout unitaire n'est valide que pour un reapprovisionnement`() {
        assertEquals(15_000L, accepted(plan(StockMovementType.RESTOCK, 5_000L, cost = 90_000L)))
        assertEquals(StockChangeError.COST_INVALID, rejected(plan(StockMovementType.RESTOCK, 5_000L, cost = -1L)))
        assertEquals(StockChangeError.COST_INVALID, rejected(plan(StockMovementType.CUSTOMER_RETURN, 1_000L, cost = 100L)))
    }

    @Test fun `le plafond de stock est respecte`() {
        assertEquals(
            StockChangeError.STOCK_OVERFLOW,
            rejected(plan(StockMovementType.RESTOCK, StockRules.MAX_QUANTITY_MILLI, current = 1_000L))
        )
        assertEquals(
            StockChangeError.STOCK_OVERFLOW,
            rejected(plan(StockMovementType.RESTOCK, 1_000L, current = Long.MAX_VALUE - 10L))
        )
    }

    @Test fun `ecart de correction`() {
        assertEquals(-4_000L, StockMovementPolicy.adjustmentDelta(6_000L, 10_000L, ProductUnit.PIECE))
        assertEquals(0L, StockMovementPolicy.adjustmentDelta(10_000L, 10_000L, ProductUnit.PIECE))
        assertEquals(500L, StockMovementPolicy.adjustmentDelta(10_500L, 10_000L, ProductUnit.KILOGRAM))
        assertNull(StockMovementPolicy.adjustmentDelta(10_500L, 10_000L, ProductUnit.PIECE))
        assertNull(StockMovementPolicy.adjustmentDelta(-1L, 10_000L, ProductUnit.KILOGRAM))
        assertNull(StockMovementPolicy.adjustmentDelta(StockRules.MAX_QUANTITY_MILLI + 1, 0L, ProductUnit.KILOGRAM))
    }
}
