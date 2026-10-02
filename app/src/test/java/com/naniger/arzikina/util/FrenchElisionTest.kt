package com.naniger.arzikina.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrenchElisionTest {

    @Test
    fun `voyelles - elision, accents et majuscules compris`() {
        listOf("Aïcha", "aïcha", "Ousmane", "Idrissa", "Ève", "Émile", "Ali", "Ursule", "  Amina").forEach {
            assertTrue(it, FrenchElision.requiresElision(it))
        }
    }

    @Test
    fun `consonnes, h et y - pas d elision`() {
        listOf("Moussa", "Halima", "Hamidou", "Yacouba", "Zeinabou", "Bachir").forEach {
            assertFalse(it, FrenchElision.requiresElision(it))
        }
    }

    @Test
    fun `nom vide - pas d elision`() {
        assertFalse(FrenchElision.requiresElision(""))
        assertFalse(FrenchElision.requiresElision("   "))
    }
}
