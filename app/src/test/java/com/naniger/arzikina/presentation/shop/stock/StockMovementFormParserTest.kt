package com.naniger.arzikina.presentation.shop.stock

import com.naniger.arzikina.domain.shop.StockChangeError
import com.naniger.arzikina.domain.shop.StockMovementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StockMovementFormParserTest {

    private fun valid(i: StockMovementFormInput) = StockMovementFormParser.parse(i) as StockMovementFormResult.Valid
    private fun errors(i: StockMovementFormInput) = (StockMovementFormParser.parse(i) as StockMovementFormResult.Invalid).errors

    @Test fun `reapprovisionnement avec cout`() {
        val r = valid(StockMovementFormInput(StockMovementType.RESTOCK, quantity = "24", unitCost = "900,50", reference = " BL-12 ", updatePurchasePrice = true))
        assertEquals(24_000L, r.quantityMilli)
        assertEquals(90_050L, r.unitCostMinor)
        assertEquals("BL-12", r.reference)
        assertTrue(r.updatePurchasePrice)
    }

    @Test fun `la mise a jour du prix d'achat exige un cout`() {
        assertFalse(valid(StockMovementFormInput(StockMovementType.RESTOCK, quantity = "1", updatePurchasePrice = true)).updatePurchasePrice)
    }

    @Test fun `le cout est ignore hors reapprovisionnement`() {
        assertNull(valid(StockMovementFormInput(StockMovementType.CUSTOMER_RETURN, quantity = "1", unitCost = "abc")).unitCostMinor)
    }

    @Test fun `quantite vide ou nulle refusee sauf correction`() {
        assertEquals(setOf(StockChangeError.ZERO_QUANTITY), errors(StockMovementFormInput(StockMovementType.RESTOCK, quantity = "")))
        assertEquals(setOf(StockChangeError.ZERO_QUANTITY), errors(StockMovementFormInput(StockMovementType.RESTOCK, quantity = "0")))
        assertEquals(0L, valid(StockMovementFormInput(StockMovementType.ADJUSTMENT, quantity = "0", reason = "Rayon vide")).quantityMilli)
    }

    @Test fun `motif obligatoire pour perte et correction`() {
        assertEquals(setOf(StockChangeError.REASON_REQUIRED), errors(StockMovementFormInput(StockMovementType.DAMAGED_OR_LOST, quantity = "1", reason = "  ")))
        assertEquals(setOf(StockChangeError.REASON_REQUIRED), errors(StockMovementFormInput(StockMovementType.ADJUSTMENT, quantity = "5")))
    }

    @Test fun `cout illisible signale`() {
        assertEquals(setOf(StockChangeError.COST_INVALID), errors(StockMovementFormInput(StockMovementType.RESTOCK, quantity = "1", unitCost = "x")))
    }

    @Test fun `sens du mouvement`() {
        assertEquals(-3_000L, StockMovementFormParser.signedDelta(StockMovementType.DAMAGED_OR_LOST, 3_000L))
        assertEquals(3_000L, StockMovementFormParser.signedDelta(StockMovementType.RESTOCK, 3_000L))
        assertEquals(3_000L, StockMovementFormParser.signedDelta(StockMovementType.CUSTOMER_RETURN, 3_000L))
    }

    @Test fun `nombre demesure refuse`() {
        assertEquals(setOf(StockChangeError.ZERO_QUANTITY), errors(StockMovementFormInput(StockMovementType.RESTOCK, quantity = "9".repeat(30))))
    }
}
