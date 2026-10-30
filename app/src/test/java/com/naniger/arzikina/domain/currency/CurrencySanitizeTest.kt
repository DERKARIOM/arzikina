package com.naniger.arzikina.domain.currency


import org.junit.Assert.*
import org.junit.Test
class CurrencySanitizeTest {
    @Test fun s() {
        assertEquals("EUR", CurrencyCatalog.sanitize(" eur ")); assertEquals("XOF", CurrencyCatalog.sanitize(null))
        assertEquals("XOF", CurrencyCatalog.sanitize("ZZZ")); assertEquals("XOF", CurrencyCatalog.sanitize("<script>")); assertEquals("GNF", CurrencyCatalog.sanitize("gnf"))
    }
}
