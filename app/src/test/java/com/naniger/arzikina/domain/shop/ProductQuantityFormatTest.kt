package com.naniger.arzikina.domain.shop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductQuantityFormatTest {

    @Test fun `parse lit entiers et decimaux avec virgule ou point`() {
        assertEquals(12_000L, ProductQuantityFormat.parse("12"))
        assertEquals(1_500L, ProductQuantityFormat.parse("1,5"))
        assertEquals(1_500L, ProductQuantityFormat.parse("1.5"))
        assertEquals(250L, ProductQuantityFormat.parse("0,25"))
        assertEquals(1L, ProductQuantityFormat.parse("0,001"))
    }

    @Test fun `parse ignore les espaces de milliers`() {
        assertEquals(1_250_000L, ProductQuantityFormat.parse("1 250"))
        assertEquals(1_250_000L, ProductQuantityFormat.parse("1 250"))
        assertEquals(1_250_000L, ProductQuantityFormat.parse("1 250"))
    }

    @Test fun `parse refuse les saisies invalides`() {
        assertNull(ProductQuantityFormat.parse(""))
        assertNull(ProductQuantityFormat.parse("  "))
        assertNull(ProductQuantityFormat.parse("abc"))
        assertNull(ProductQuantityFormat.parse("-1"))
        assertNull(ProductQuantityFormat.parse("1,0001"))
        assertNull(ProductQuantityFormat.parse("1,2,3"))
    }

    @Test fun `parse accepte les zeros de fin au dela de trois decimales`() {
        assertEquals(1_500L, ProductQuantityFormat.parse("1,500000"))
    }

    @Test fun `parse refuse un depassement de Long`() {
        assertNull(ProductQuantityFormat.parse("99999999999999999999"))
    }

    @Test fun `format affiche sans decimale inutile`() {
        assertEquals("12", ProductQuantityFormat.format(12_000L))
        assertEquals("1,5", ProductQuantityFormat.format(1_500L))
        assertEquals("0,001", ProductQuantityFormat.format(1L))
        assertEquals("0", ProductQuantityFormat.format(0L))
    }

    @Test fun `formatForInput est relisible par parse`() {
        listOf(0L, 1L, 250L, 1_500L, 12_000L, 1_250_000L, 987_654_321L).forEach { milli ->
            assertEquals(milli, ProductQuantityFormat.parse(ProductQuantityFormat.formatForInput(milli)))
        }
    }

    @Test fun `formatForInput n'utilise pas de notation scientifique`() {
        assertEquals("10000", ProductQuantityFormat.formatForInput(10_000_000L))
        assertEquals("1,5", ProductQuantityFormat.formatForInput(1_500L))
    }
}
