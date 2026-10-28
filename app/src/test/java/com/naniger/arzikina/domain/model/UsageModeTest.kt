package com.naniger.arzikina.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageModeTest {

    @Test
    fun `boutique uniquement cree un compte Boutique et pas de compte Especes`() {
        assertTrue(UsageMode.SHOP.createsShopAccount)
        assertFalse(UsageMode.SHOP.createsCashAccount)
    }

    @Test
    fun `finances personnelles cree un compte Especes et pas de compte Boutique`() {
        assertFalse(UsageMode.PERSONAL.createsShopAccount)
        assertTrue(UsageMode.PERSONAL.createsCashAccount)
    }

    @Test
    fun `les deux modes creent les deux comptes`() {
        assertTrue(UsageMode.BOTH.createsShopAccount)
        assertTrue(UsageMode.BOTH.createsCashAccount)
    }

    @Test
    fun `fromCode relit chaque valeur stockee`() {
        UsageMode.entries.forEach { assertEquals(it, UsageMode.fromCode(it.name)) }
    }

    @Test
    fun `fromCode tolere une valeur absente ou inconnue`() {
        assertNull(UsageMode.fromCode(null))
        assertNull(UsageMode.fromCode(""))
        assertNull(UsageMode.fromCode("shop"))
        assertNull(UsageMode.fromCode("AUTRE"))
    }

    @Test
    fun `les noms stockes ne changent jamais`() {
        assertEquals(listOf("SHOP", "PERSONAL", "BOTH"), UsageMode.entries.map { it.name })
    }
}
