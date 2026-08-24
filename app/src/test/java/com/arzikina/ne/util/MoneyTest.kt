package com.arzikina.ne.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Couvre le socle du formatage/parsing des montants (voir la doc de tête de [Money]) : c'est la
 * base sur laquelle s'appuient tous les formulaires de saisie (Étapes 3 à 6 du chantier
 * "uniformisation du formatage des montants") — toute régression ici se répercute partout.
 */
class MoneyTest {

    // --- parseToMinorUnits : tolérance aux espaces de milliers ---------------------------------

    @Test
    fun `parseToMinorUnits sans separateur de milliers`() {
        assertEquals(1_000_000L, Money.parseToMinorUnits("10000"))
    }

    @Test
    fun `parseToMinorUnits avec espace normale comme separateur de milliers`() {
        assertEquals(1_000_000L, Money.parseToMinorUnits("10 000"))
    }

    @Test
    fun `parseToMinorUnits avec espace insecable comme separateur de milliers`() {
        assertEquals(1_000_000L, Money.parseToMinorUnits("10 000"))
    }

    @Test
    fun `parseToMinorUnits avec espace fine insecable comme separateur de milliers`() {
        assertEquals(1_000_000L, Money.parseToMinorUnits("10 000"))
    }

    @Test
    fun `parseToMinorUnits avec plusieurs groupes de milliers`() {
        assertEquals(1_500_000_000L, Money.parseToMinorUnits("15 000 000"))
    }

    @Test
    fun `parseToMinorUnits colle depuis un affichage deja formate avec decimales`() {
        assertEquals(150_050L, Money.parseToMinorUnits("1 500,50"))
    }

    @Test
    fun `parseToMinorUnits format brut avec point decimal reste supporte`() {
        assertEquals(1_000_050L, Money.parseToMinorUnits("10000.50"))
    }

    @Test
    fun `parseToMinorUnits virgule decimale sans separateur de milliers`() {
        assertEquals(150_050L, Money.parseToMinorUnits("1500,50"))
    }

    @Test
    fun `parseToMinorUnits chaine vide retourne null`() {
        assertNull(Money.parseToMinorUnits(""))
    }

    @Test
    fun `parseToMinorUnits uniquement des espaces retourne null`() {
        assertNull(Money.parseToMinorUnits("   "))
    }

    @Test
    fun `parseToMinorUnits montant negatif retourne null`() {
        assertNull(Money.parseToMinorUnits("-1000"))
    }

    @Test
    fun `parseToMinorUnits texte invalide retourne null`() {
        assertNull(Money.parseToMinorUnits("abc"))
    }

    @Test
    fun `parseToMinorUnits zero est valide`() {
        assertEquals(0L, Money.parseToMinorUnits("0"))
    }

    // --- formatForInput : matrice de tests minimale du cahier des charges ----------------------

    @Test
    fun `formatForInput 100 reste 100`() {
        assertEquals("100", Money.formatForInput(10_000L))
    }

    @Test
    fun `formatForInput 1000 devient 1 000`() {
        assertEquals("1 000", Money.formatForInput(100_000L))
    }

    @Test
    fun `formatForInput 10000 devient 10 000`() {
        assertEquals("10 000", Money.formatForInput(1_000_000L))
    }

    @Test
    fun `formatForInput 100000 devient 100 000`() {
        assertEquals("100 000", Money.formatForInput(10_000_000L))
    }

    @Test
    fun `formatForInput 1000000 devient 1 000 000`() {
        assertEquals("1 000 000", Money.formatForInput(100_000_000L))
    }

    @Test
    fun `formatForInput 15000000 devient 15 000 000`() {
        assertEquals("15 000 000", Money.formatForInput(1_500_000_000L))
    }

    @Test
    fun `formatForInput 10000-50 devient 10 000,50`() {
        assertEquals("10 000,50", Money.formatForInput(1_000_050L))
    }

    @Test
    fun `formatForInput 10000-00 devient 10 000 sans decimales`() {
        assertEquals("10 000", Money.formatForInput(1_000_000L))
    }

    @Test
    fun `formatForInput zero`() {
        assertEquals("0", Money.formatForInput(0L))
    }

    // --- Aller-retour formatForInput -> parseToMinorUnits (round-trip) -------------------------

    @Test
    fun `round-trip formatForInput puis parseToMinorUnits preserve la valeur`() {
        val values = listOf(0L, 10_000L, 100_000L, 1_000_000L, 10_000_000L, 100_000_000L, 1_500_000_000L, 1_000_050L)
        for (minorUnits in values) {
            val formatted = Money.formatForInput(minorUnits)
            assertEquals(
                "round-trip a echoue pour $minorUnits (formate en \"$formatted\")",
                minorUnits,
                Money.parseToMinorUnits(formatted)
            )
        }
    }

    @Test
    fun `formatForInput puis coller le resultat redonne le meme montant`() {
        // Simule : préremplissage d'un champ en mode édition, l'utilisateur ne touche à rien,
        // sauvegarde immédiate — la valeur stockée en base ne doit jamais dériver.
        val original = 1_000_050L
        val displayed = Money.formatForInput(original)
        assertEquals(original, Money.parseToMinorUnits(displayed))
    }
}
