package com.naniger.arzikina.domain.shop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SalesStatsTest {

    @Test fun averageBasketIsNullWithoutSale() {
        assertNull(SalesSummary.EMPTY.averageBasketMinor)
    }

    @Test fun averageBasketIsRoundedHalfUp() {
        // 1000 / 3 = 333,33 -> 333 ; 1001 / 2 = 500,5 -> 501
        assertEquals(333L, SalesSummary(3, 1000, 1000, 0, 0).averageBasketMinor)
        assertEquals(501L, SalesSummary(2, 1001, 1001, 0, 0).averageBasketMinor)
    }

    @Test fun remainingDueIsRevenueMinusCollectedNeverNegative() {
        assertEquals(0L, SalesSummary(1, 500, 500, 0, 0).remainingDueMinor)
        assertEquals(200L, SalesSummary(1, 500, 300, 0, 0).remainingDueMinor)
        assertEquals(0L, SalesSummary(1, 500, 700, 0, 0).remainingDueMinor)
    }

    @Test fun cancelledSalesDoNotChangeRevenueFields() {
        val s = SalesSummary(salesCount = 2, revenueMinor = 900, collectedMinor = 900, cancelledCount = 1, cancelledMinor = 400)
        assertEquals(450L, s.averageBasketMinor)
        assertEquals(400L, s.cancelledMinor)
    }

    @Test fun dayKeyUsesLocalOffset() {
        val offset = 3_600_000L // UTC+1 (Niamey)
        val utcMidnight = 20_000L * SalesStatsRules.DAY_MILLIS
        // 23:30 UTC la veille = 00:30 locale du jour 20 000
        assertEquals(20_000L, SalesStatsRules.dayKey(utcMidnight - 30 * 60_000L, offset))
        assertEquals(19_999L, SalesStatsRules.dayKey(utcMidnight - 90 * 60_000L, offset))
    }

    @Test fun fillGapsProducesContinuousSeries() {
        val data = listOf(DailyRevenue(10, 500, 2), DailyRevenue(13, 300, 1))
        val filled = SalesStatsRules.fillGaps(10, 14, data)
        assertEquals(listOf(10L, 11L, 12L, 13L, 14L), filled.map { it.dayKey })
        assertEquals(listOf(500L, 0L, 0L, 300L, 0L), filled.map { it.revenueMinor })
        assertEquals(emptyList<DailyRevenue>(), SalesStatsRules.fillGaps(5, 4, data))
    }
}
