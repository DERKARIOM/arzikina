package com.naniger.arzikina.data.local.database
import com.naniger.arzikina.domain.model.DefaultAccountKey
import org.junit.Assert.*
import org.junit.Test
class DefaultAccountsCurrencyTest {
    @Test fun usesGivenCurrency() {
        val l = DefaultAccounts.seedFor(1L, 7L, listOf(DefaultAccountKey.CASH, DefaultAccountKey.SHOP), currencyCode = "EUR")
        assertEquals(listOf("EUR", "EUR"), l.map { it.currencyCode })
    }
    @Test fun defaultsToXof() {
        assertEquals("XOF", DefaultAccounts.seedFor(1L, 7L, listOf(DefaultAccountKey.CASH)).single().currencyCode)
    }
}
