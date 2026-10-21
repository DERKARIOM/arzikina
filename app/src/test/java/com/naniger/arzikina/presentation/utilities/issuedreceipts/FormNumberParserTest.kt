package com.naniger.arzikina.presentation.utilities.issuedreceipts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormNumberParserTest {
    @Test fun quantities() {
        assertEquals(1000L, FormNumberParser.quantityMilli("1"))
        assertEquals(2500L, FormNumberParser.quantityMilli("2,5"))
        assertEquals(2500L, FormNumberParser.quantityMilli("2.5"))
        assertEquals(500L, FormNumberParser.quantityMilli(".5"))
        assertEquals(1235L, FormNumberParser.quantityMilli("1,2345"))
        assertEquals(1_000_000L, FormNumberParser.quantityMilli("1 000"))
        assertEquals(0L, FormNumberParser.quantityMilli("0"))
    }

    @Test fun percentages() {
        assertEquals(1800, FormNumberParser.basisPoints("18"))
        assertEquals(750, FormNumberParser.basisPoints("7,5"))
        assertEquals(5, FormNumberParser.basisPoints("0.05"))
    }

    @Test fun rejectsGarbage() {
        for (bad in listOf("", " ", "abc", "-1", "1,2,3", "1e5", "+3", "99999999999999999999")) {
            assertNull(bad, FormNumberParser.quantityMilli(bad))
        }
    }

    @Test fun formatIsInverseOfParse() {
        for (v in listOf(0L, 1L, 500L, 1_000L, 1_500L, 2_345L)) {
            assertEquals(v, FormNumberParser.quantityMilli(FormNumberParser.format(v, 3)))
        }
        assertEquals("1,5", FormNumberParser.format(1_500L, 3))
        assertEquals("7,5", FormNumberParser.format(750L, 2))
        assertEquals("18", FormNumberParser.format(1_800L, 2))
    }
}
