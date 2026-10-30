package com.naniger.arzikina.domain.currency

import org.junit.Assert.*
import org.junit.Test

class CurrencyCatalogAndFormatterTest {
    @Test fun xofFormat() { assertEquals("10\u202f000 FCFA", CurrencyFormatter.format(1_000_000, "XOF")) }
    @Test fun eurWithCents() { assertEquals("1\u202f234,50 €", CurrencyFormatter.format(123_450, "EUR")) }
    @Test fun negative() { assertEquals("-500 FCFA", CurrencyFormatter.format(-50_000, "XOF")) }
    @Test fun unknownFallsBackToCode() { assertEquals("5 ZZZ", CurrencyFormatter.format(500, "zzz")) }
    @Test fun noDuplicateCodes() { assertEquals(CurrencyCatalog.all.size, CurrencyCatalog.all.map { it.code }.toSet().size) }
    @Test fun codesAreIso() { assertTrue(CurrencyCatalog.all.all { Regex("[A-Z]{3}").matches(it.code) }) }
    @Test fun requiredCurrenciesPresent() {
        listOf("XOF","XAF","EUR","USD","GNF","NGN","GHS","MAD","GBP","CAD","CHF").forEach { assertTrue(it, CurrencyCatalog.isKnown(it)) }
    }
    @Test fun featuredFirst() { assertEquals("XOF", CurrencyCatalog.all.first().code); assertEquals("CHF", CurrencyCatalog.all[10].code) }
    @Test fun searchAccents() { assertTrue(CurrencyCatalog.search("sénégal").any { it.code == "XOF" }); assertTrue(CurrencyCatalog.search("senegal").any { it.code == "XOF" }) }
    @Test fun searchByCodeAndName() {
        assertEquals("NGN", CurrencyCatalog.search("naira") { if (it.code == "NGN") "Naira nigérian" else "" }.single().code)
        assertTrue(CurrencyCatalog.search("eur").any { it.code == "EUR" })
    }
    @Test fun emptySearchReturnsAll() { assertEquals(CurrencyCatalog.all.size, CurrencyCatalog.search("  ").size) }
    @Test fun defaults() {
        assertEquals("XOF", CurrencyCatalog.defaultFor("NE")); assertEquals("XOF", CurrencyCatalog.defaultFor(null))
        assertEquals("XAF", CurrencyCatalog.defaultFor("cm")); assertEquals("XOF", CurrencyCatalog.defaultFor("JP"))
        assertEquals("EUR", CurrencyCatalog.defaultFor("FR"))
    }
    @Test fun formatLeavesStoredValueUntouched() {
        val v = 123_456L; CurrencyFormatter.format(v, "XOF"); CurrencyFormatter.format(v, "EUR"); assertEquals(123_456L, v)
    }
}
