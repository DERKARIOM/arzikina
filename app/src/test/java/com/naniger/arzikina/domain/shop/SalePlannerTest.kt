package com.naniger.arzikina.domain.shop

import com.naniger.arzikina.domain.model.ReceiptDiscount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SalePlannerTest {

    private fun product(
        id: Long,
        price: Long,
        stock: Long,
        unit: ProductUnit = ProductUnit.PIECE,
        cost: Long? = null,
        archived: Boolean = false
    ) = SaleProductSnapshot(id, "P$id", unit, price, cost, stock, archived)

    private val catalog = mapOf(
        1L to product(1, 2_500, 10_000, cost = 1_800),
        2L to product(2, 3_000, 5_000),
        3L to product(3, 1_500, 2_500, ProductUnit.KILOGRAM),
        4L to product(4, 900, 1_000, archived = true)
    )

    private fun plan(
        lines: List<SaleLineRequest>,
        discount: ReceiptDiscount = ReceiptDiscount.None,
        name: String? = null,
        phone: String? = null
    ) = SalePlanner.plan(lines, discount, name, phone, catalog)

    @Test fun `exemple du cahier des charges - 2 x A et 1 x B egal 8000`() {
        val plan = plan(listOf(SaleLineRequest(1, 2_000), SaleLineRequest(2, 1_000)))
        assertEquals(8_000L, plan.totalMinor)
        assertEquals(8_000L, plan.subtotalMinor)
        assertEquals(0L, plan.discountMinor)
        assertEquals(listOf(5_000L, 3_000L), plan.lines.map { it.lineTotalMinor })
        assertEquals(listOf(8_000L, 4_000L), plan.lines.map { it.newStockMilli })
    }

    @Test fun `prix et cout sont figes depuis le produit`() {
        val line = plan(listOf(SaleLineRequest(1, 1_000))).lines.single()
        assertEquals(2_500L, line.unitPriceMinor)
        assertEquals(1_800L, line.unitCostMinor)
        assertEquals("P1", line.productName)
    }

    @Test fun `panier vide refuse`() {
        assertThrows(SaleException.EmptyCart::class.java) { plan(emptyList()) }
    }

    @Test fun `produit inconnu ou archive refuse`() {
        assertThrows(SaleException.ProductUnavailable::class.java) { plan(listOf(SaleLineRequest(99, 1_000))) }
        assertThrows(SaleException.ProductUnavailable::class.java) { plan(listOf(SaleLineRequest(4, 1_000))) }
    }

    @Test fun `stock insuffisant refuse - jamais de stock negatif`() {
        val e = assertThrows(SaleException.InsufficientStock::class.java) { plan(listOf(SaleLineRequest(2, 6_000))) }
        assertEquals(listOf(2L), e.productIds)
    }

    @Test fun `vendre exactement tout le stock est possible`() {
        assertEquals(0L, plan(listOf(SaleLineRequest(2, 5_000))).lines.single().newStockMilli)
    }

    @Test fun `lignes du meme produit cumulees avant le controle de stock`() {
        assertThrows(SaleException.InsufficientStock::class.java) {
            plan(listOf(SaleLineRequest(2, 3_000), SaleLineRequest(2, 3_000)))
        }
        val ok = plan(listOf(SaleLineRequest(2, 1_000), SaleLineRequest(2, 2_000)))
        assertEquals(1, ok.lines.size)
        assertEquals(3_000L, ok.lines.single().quantityMilli)
    }

    @Test fun `quantite nulle refusee`() {
        val e = assertThrows(SaleException.InvalidQuantity::class.java) { plan(listOf(SaleLineRequest(1, 0))) }
        assertEquals(StockMovementError.ZERO_QUANTITY, e.error)
    }

    @Test fun `fraction refusee pour une piece, acceptee pour un kilogramme`() {
        val e = assertThrows(SaleException.InvalidQuantity::class.java) { plan(listOf(SaleLineRequest(1, 1_500))) }
        assertEquals(StockMovementError.FRACTION_NOT_ALLOWED, e.error)
        val kg = plan(listOf(SaleLineRequest(3, 500)))
        assertEquals(750L, kg.totalMinor)
        assertEquals(2_000L, kg.lines.single().newStockMilli)
    }

    @Test fun `remise en pourcentage`() {
        val p = plan(listOf(SaleLineRequest(1, 2_000)), ReceiptDiscount.Percent(1_000))
        assertEquals(5_000L, p.subtotalMinor)
        assertEquals(500L, p.discountMinor)
        assertEquals(4_500L, p.totalMinor)
    }

    @Test fun `remise fixe plafonnee au sous-total donne un total nul refuse`() {
        assertThrows(SaleException.ZeroTotal::class.java) {
            plan(listOf(SaleLineRequest(2, 1_000)), ReceiptDiscount.Fixed(10_000))
        }
    }

    @Test fun `remise invalide refusee`() {
        assertThrows(SaleException.InvalidAmount::class.java) {
            plan(listOf(SaleLineRequest(2, 1_000)), ReceiptDiscount.Fixed(-1))
        }
        assertThrows(SaleException.InvalidAmount::class.java) {
            plan(listOf(SaleLineRequest(2, 1_000)), ReceiptDiscount.Percent(10_001))
        }
    }

    @Test fun `produit gratuit seul donne un total nul refuse`() {
        val free = mapOf(7L to product(7, 0, 1_000))
        assertThrows(SaleException.ZeroTotal::class.java) {
            SalePlanner.plan(listOf(SaleLineRequest(7, 1_000)), ReceiptDiscount.None, null, null, free)
        }
    }

    @Test fun `nom et telephone client nettoyes`() {
        val p = plan(listOf(SaleLineRequest(1, 1_000)), name = "  Awa   Diallo ", phone = " +227 90-00 (12)ab ")
        assertEquals("Awa Diallo", p.customerName)
        assertEquals("+227 90-00 (12)", p.customerPhone)
    }

    @Test fun `client vide devient null et longueurs plafonnees`() {
        val p = plan(listOf(SaleLineRequest(1, 1_000)), name = "   ", phone = "")
        assertNull(p.customerName)
        assertNull(p.customerPhone)
        assertEquals(SalePlanner.MAX_CUSTOMER_NAME, SalePlanner.cleanName("x".repeat(500))!!.length)
        assertEquals(SalePlanner.MAX_CUSTOMER_PHONE, SalePlanner.cleanPhone("1".repeat(500))!!.length)
    }

    @Test fun `total identique au calculateur de recu`() {
        val p = plan(listOf(SaleLineRequest(1, 3_000), SaleLineRequest(3, 1_250)), ReceiptDiscount.Percent(750))
        val expected = SaleCalculator.totalsOrNull(
            listOf(SaleLineDraft(1, 2_500, 3_000), SaleLineDraft(3, 1_500, 1_250)),
            ReceiptDiscount.Percent(750)
        )!!
        assertEquals(expected.totalMinor, p.totalMinor)
        assertTrue(p.totalMinor > 0)
    }

    @Test fun `somme des lignes egale sous-total`() {
        val p = plan(listOf(SaleLineRequest(1, 3_000), SaleLineRequest(2, 2_000), SaleLineRequest(3, 333)))
        assertEquals(p.subtotalMinor, p.lines.sumOf { it.lineTotalMinor })
    }
}
