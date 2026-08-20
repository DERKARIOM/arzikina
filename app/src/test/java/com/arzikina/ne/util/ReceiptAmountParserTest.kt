package com.arzikina.ne.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Voir la doc de tête de [ReceiptAmountParser] : ces exemples sont des textes SYNTHÉTIQUES,
 * représentatifs des formats les plus courants observés sur des reçus Mobile Money — pas des
 * extraits réels (aucun exemple réel disponible au moment de l'écriture). À enrichir avec de vrais
 * textes extraits ([com.arzikina.ne.data.receipts.ReceiptTextExtractor]) dès que possible pour
 * fiabiliser davantage l'heuristique (voir Étape 6, "Vérification finale").
 */
class ReceiptAmountParserTest {

    @Test
    fun `montant sur la meme ligne que le mot-cle, sans decimales`() {
        val text = """
            Orange Money
            Montant : 15 000 FCFA
            Frais : 100 FCFA
            Nouveau solde : 42 500 FCFA
        """.trimIndent()

        assertEquals(1_500_000L, ReceiptAmountParser.parseAmount(text))
    }

    @Test
    fun `montant sur la ligne suivant le mot-cle`() {
        val text = """
            Reçu de transaction
            Montant transféré
            25.000 FCFA
            Référence : 123456789
        """.trimIndent()

        assertEquals(2_500_000L, ReceiptAmountParser.parseAmount(text))
    }

    @Test
    fun `montant avec separateur decimal reel`() {
        val text = "Montant total : 1 500,50 EUR"

        assertEquals(150_050L, ReceiptAmountParser.parseAmount(text))
    }

    @Test
    fun `ligne de frais ignoree meme si elle contient un mot-cle`() {
        val text = """
            Frais de transaction : 500 FCFA
            Montant envoyé : 10 000 FCFA
        """.trimIndent()

        assertEquals(1_000_000L, ReceiptAmountParser.parseAmount(text))
    }

    @Test
    fun `mot-cle generique utilise seulement en repli`() {
        val text = "Total : 7 500 FCFA"

        assertEquals(750_000L, ReceiptAmountParser.parseAmount(text))
    }

    @Test
    fun `aucun mot-cle reconnu retourne null`() {
        val text = """
            Merci pour votre confiance.
            Référence 998877
        """.trimIndent()

        assertNull(ReceiptAmountParser.parseAmount(text))
    }

    @Test
    fun `ligne contenant un mot-cle ET un mot exclu est quand meme ignoree`() {
        // "total" est un mot-clé (confiance générique) mais "solde" l'exclut explicitement — sans
        // ce garde-fou, ce test échouerait en retournant le solde du compte au lieu de `null`.
        val text = "Solde total : 42 500 FCFA"

        assertNull(ReceiptAmountParser.parseAmount(text))
    }

    @Test
    fun `texte vide retourne null`() {
        assertNull(ReceiptAmountParser.parseAmount(""))
    }
}
