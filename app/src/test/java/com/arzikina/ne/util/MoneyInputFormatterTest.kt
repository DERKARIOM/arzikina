package com.arzikina.ne.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Couvre le cœur pur (sans dépendance Android) de [MoneyInputFormatter] : nettoyage, regroupement
 * et calcul de la position du curseur. Le [android.text.TextWatcher] lui-même (branchement sur un
 * [android.widget.EditText]) n'est pas testable ici — aucun harnais Robolectric dans ce projet
 * (même limite que le câblage `doAfterTextChanged` des Fragments, jamais testé unitairement) — mais
 * cette logique textuelle est la partie qui comporte le risque réel (curseur, troncature).
 */
class MoneyInputFormatterTest {

    // --- clean() ---------------------------------------------------------------------------

    @Test
    fun `clean ne garde que les chiffres pour un entier`() {
        assertEquals("10000", MoneyInputFormatter.clean("10000"))
    }

    @Test
    fun `clean normalise le point en virgule`() {
        assertEquals("10000,00", MoneyInputFormatter.clean("10000.00"))
    }

    @Test
    fun `clean supprime les espaces de milliers`() {
        assertEquals("10000", MoneyInputFormatter.clean("10 000"))
    }

    @Test
    fun `clean supprime les espaces insecables`() {
        assertEquals("1500,50", MoneyInputFormatter.clean("1 500,50"))
    }

    @Test
    fun `clean limite a 2 decimales`() {
        assertEquals("10000,12", MoneyInputFormatter.clean("10000,1234"))
    }

    @Test
    fun `clean ignore un second separateur decimal`() {
        assertEquals("1000,50", MoneyInputFormatter.clean("1000,50,99"))
    }

    @Test
    fun `clean supprime les lettres et symboles`() {
        assertEquals("10000", MoneyInputFormatter.clean("10000 FCFA"))
    }

    @Test
    fun `clean chaine vide reste vide`() {
        assertEquals("", MoneyInputFormatter.clean(""))
    }

    // --- regroup() -------------------------------------------------------------------------

    @Test
    fun `regroup partie entiere seule`() {
        assertEquals("10 000", MoneyInputFormatter.regroup("10000"))
    }

    @Test
    fun `regroup avec decimales`() {
        assertEquals("1 500,50", MoneyInputFormatter.regroup("1500,50"))
    }

    @Test
    fun `regroup nombre court sans espace`() {
        assertEquals("100", MoneyInputFormatter.regroup("100"))
    }

    @Test
    fun `regroup decimales sans partie entiere`() {
        assertEquals(",5", MoneyInputFormatter.regroup(",5"))
    }

    // --- countSignificant() / positionForSignificantCount() : cursor round-trip ------------

    @Test
    fun `countSignificant ignore les espaces`() {
        assertEquals(2, MoneyInputFormatter.countSignificant("1 0", 3))
    }

    @Test
    fun `positionForSignificantCount place le curseur apres le Nieme chiffre en sautant les espaces`() {
        // "10 000" : les 5 chiffres significatifs sont aux index 0,1,3,4,5 (2 est un espace)
        assertEquals(1, MoneyInputFormatter.positionForSignificantCount("10 000", 1))
        assertEquals(2, MoneyInputFormatter.positionForSignificantCount("10 000", 2))
        assertEquals(4, MoneyInputFormatter.positionForSignificantCount("10 000", 3))
        assertEquals(6, MoneyInputFormatter.positionForSignificantCount("10 000", 5))
    }

    @Test
    fun `positionForSignificantCount targetCount zero place le curseur au debut`() {
        assertEquals(0, MoneyInputFormatter.positionForSignificantCount("10 000", 0))
    }

    @Test
    fun `positionForSignificantCount targetCount superieur au nombre de chiffres place le curseur a la fin`() {
        assertEquals(6, MoneyInputFormatter.positionForSignificantCount("10 000", 99))
    }

    @Test
    fun `round-trip typing simule 1 puis 10 puis 100 puis 1000 puis 10000`() {
        // Reproduit la progression exigée par le cahier des charges (section 1), en simulant
        // à chaque étape : texte brut = valeur precedente + un chiffre tape a la fin.
        var displayed = ""
        val digitsTyped = listOf("1", "0", "0", "0", "0")
        val expectedAfterEachDigit = listOf("1", "10", "100", "1 000", "10 000")

        for ((index, digit) in digitsTyped.withIndex()) {
            val raw = displayed + digit
            val cleaned = MoneyInputFormatter.clean(raw)
            displayed = MoneyInputFormatter.regroup(cleaned)
            assertEquals("apres avoir tape \"$digit\" (etape ${index + 1})", expectedAfterEachDigit[index], displayed)
        }
    }
}
