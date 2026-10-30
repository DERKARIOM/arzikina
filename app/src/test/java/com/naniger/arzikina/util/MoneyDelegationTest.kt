package com.naniger.arzikina.util
import com.naniger.arzikina.domain.currency.CurrencyFormatter
import com.naniger.arzikina.domain.model.CurrencyAmount
import org.junit.Assert.*
import org.junit.Test
class MoneyDelegationTest {
    private fun n(t: String) = t.replace(' ',' ').replace(' ',' ')
    @Test fun formatDelegates() { assertEquals(CurrencyFormatter.format(1_000_000, "XOF"), Money.format(CurrencyAmount("XOF", 1_000_000))) }
    @Test fun xofSymbol() { assertEquals("10 000 FCFA", n(Money.format(CurrencyAmount("XOF", 1_000_000)))) }
    @Test fun formatAmountNoCents() { assertEquals("10 000", n(Money.formatAmount(1_000_000))) }
    @Test fun formatAmountCents() { assertEquals("10 000,50", n(Money.formatAmount(1_000_050))) }
    @Test fun symbols() { assertEquals("FCFA", Money.symbolOf("XAF")); assertEquals("£", Money.symbolOf("GBP")); assertEquals("ZZZ", Money.symbolOf("ZZZ")) }
    @Test fun roundTripParse() { val v = 1_234_550L; assertEquals(v, Money.parseToMinorUnits(Money.formatAmount(v))) }
    @Test fun storedValueUnchanged() { val v = 987_654L; Money.format(CurrencyAmount("EUR", v)); Money.format(CurrencyAmount("XOF", v)); assertEquals(987_654L, v) }
}
