package com.naniger.arzikina.presentation.auth.currency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencySelectionTest {

    @Test fun `par defaut XOF et rien de touche`() {
        val s = CurrencySelection()
        assertEquals("XOF", s.code)
        assertFalse(s.touched)
    }

    @Test fun `la region suit l'appareil tant que l'utilisateur n'a pas choisi`() {
        assertEquals("XOF", CurrencySelection().withRegionDefault("NE").code)
        assertEquals("EUR", CurrencySelection().withRegionDefault("FR").code)
        assertEquals("XOF", CurrencySelection().withRegionDefault("JP").code)
        assertEquals("XOF", CurrencySelection().withRegionDefault(null).code)
    }

    @Test fun `un choix explicite n'est jamais ecrase par la region`() {
        val s = CurrencySelection().select("GNF").withRegionDefault("FR")
        assertEquals("GNF", s.code)
        assertTrue(s.touched)
    }

    @Test fun `un code inconnu retombe sur la valeur par defaut`() {
        assertEquals("XOF", CurrencySelection().select("ZZZ").code)
    }

    @Test fun `la recherche ne change pas la selection`() {
        val s = CurrencySelection().select("EUR").search("dol")
        assertEquals("EUR", s.code)
        assertEquals("dol", s.query)
    }

    @Test fun `la presélection n'est pas deduite de la langue`() {
        // Seul le pays est utilisé : un pays hors catalogue de defauts donne XOF, quelle que soit la langue.
        assertEquals("XOF", CurrencySelection().withRegionDefault("").code)
    }
}
