package com.naniger.arzikina.domain.shop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SaleReferenceTest {

    @Test fun `format V-AAAAMMJJ-XXXX`() {
        val ref = SaleReference.generate(2026, 10, 5, Random(42))
        assertTrue(ref, Regex("^V-20261005-[A-HJ-NP-Z2-9]{4}$").matches(ref))
    }

    @Test fun `deterministe avec une graine, different avec une autre`() {
        assertEquals(SaleReference.generate(2026, 1, 2, Random(1)), SaleReference.generate(2026, 1, 2, Random(1)))
        assertNotEquals(SaleReference.generate(2026, 1, 2, Random(1)), SaleReference.generate(2026, 1, 2, Random(2)))
    }

    @Test fun `aucun caractere ambigu`() {
        repeat(200) {
            val suffix = SaleReference.generate(2026, 1, 1, Random(it)).takeLast(4)
            assertTrue(suffix, suffix.none { c -> c in "01IO" })
        }
    }

    @Test fun `date invalide refusee`() {
        assertThrows(IllegalArgumentException::class.java) { SaleReference.generate(2026, 13, 1) }
        assertThrows(IllegalArgumentException::class.java) { SaleReference.generate(2026, 1, 0) }
    }
}
