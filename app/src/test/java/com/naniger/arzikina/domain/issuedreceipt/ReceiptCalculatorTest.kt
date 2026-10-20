package com.naniger.arzikina.domain.issuedreceipt

import com.naniger.arzikina.domain.model.ReceiptDiscount
import com.naniger.arzikina.domain.model.ReceiptLine
import com.naniger.arzikina.domain.model.ReceiptPricing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptCalculatorTest {

    private fun line(quantityMilli: Long, unitPriceMinor: Long) = ReceiptLine("Article", quantityMilli, unitPriceMinor)

    @Test
    fun `simple line without discount or tax`() {
        val totals = ReceiptCalculator.totalsOrNull(ReceiptPricing(listOf(line(2_000, 500_000))))
        assertEquals(1_000_000L, totals?.subtotalMinor)
        assertEquals(1_000_000L, totals?.totalMinor)
        assertEquals(0L, totals?.discountMinor)
        assertEquals(0L, totals?.taxMinor)
    }

    @Test
    fun `fractional quantity is rounded half up to the minor unit`() {
        // 2,5 × 333 = 832,5 → 833
        assertEquals(833L, ReceiptCalculator.lineTotalOrNull(line(2_500, 333)))
        // 0,001 × 499 = 0,499 → 0
        assertEquals(0L, ReceiptCalculator.lineTotalOrNull(line(1, 499)))
    }

    @Test
    fun `percent discount then tax applies tax on the discounted base`() {
        val pricing = ReceiptPricing(
            lines = listOf(line(1_000, 1_000_000)),
            discount = ReceiptDiscount.Percent(1_000), // 10 %
            taxBasisPoints = 1_800 // 18 %
        )
        val totals = ReceiptCalculator.totalsOrNull(pricing)!!
        assertEquals(1_000_000L, totals.subtotalMinor)
        assertEquals(100_000L, totals.discountMinor)
        assertEquals(900_000L, totals.taxableMinor)
        assertEquals(162_000L, totals.taxMinor)
        assertEquals(1_062_000L, totals.totalMinor)
    }

    @Test
    fun `fixed discount is capped at the subtotal`() {
        val totals = ReceiptCalculator.totalsOrNull(
            ReceiptPricing(listOf(line(1_000, 10_000)), discount = ReceiptDiscount.Fixed(50_000))
        )!!
        assertEquals(10_000L, totals.discountMinor)
        assertEquals(0L, totals.totalMinor)
    }

    @Test
    fun `fractional percent such as 5,5 percent is supported`() {
        val totals = ReceiptCalculator.totalsOrNull(ReceiptPricing(listOf(line(1_000, 10_000)), taxBasisPoints = 550))!!
        assertEquals(550L, totals.taxMinor)
        assertEquals(10_550L, totals.totalMinor)
    }

    @Test
    fun `several lines are summed`() {
        val totals = ReceiptCalculator.totalsOrNull(
            ReceiptPricing(listOf(line(1_000, 25_000), line(3_000, 10_000), line(500, 20_000)))
        )!!
        assertEquals(65_000L, totals.subtotalMinor)
    }

    @Test
    fun `invalid values and overflow return null`() {
        assertNull(ReceiptCalculator.totalsOrNull(ReceiptPricing(listOf(line(0, 100)))))
        assertNull(ReceiptCalculator.totalsOrNull(ReceiptPricing(listOf(line(1_000, -1)))))
        assertNull(ReceiptCalculator.totalsOrNull(ReceiptPricing(listOf(line(1_000, 100)), taxBasisPoints = 10_001)))
        assertNull(ReceiptCalculator.totalsOrNull(ReceiptPricing(listOf(line(1_000, ReceiptCalculator.MAX_AMOUNT_MINOR + 1)))))
        assertNull(
            ReceiptCalculator.totalsOrNull(
                ReceiptPricing(listOf(line(1_000, ReceiptCalculator.MAX_AMOUNT_MINOR), line(1_000, 1)))
            )
        )
        assertNotNull(ReceiptCalculator.totalsOrNull(ReceiptPricing(listOf(line(1_000, ReceiptCalculator.MAX_AMOUNT_MINOR)))))
    }
}
