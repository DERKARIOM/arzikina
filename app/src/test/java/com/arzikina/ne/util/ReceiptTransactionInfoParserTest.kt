package com.arzikina.ne.util

import com.arzikina.ne.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Voir la doc de tête de [ReceiptTransactionInfoParser]. [MYNITA_RECEIPT_TEXT] est le texte RÉEL
 * extrait d'un reçu MyNITA (transfert d'argent, Niger) fourni pendant la conception de cette
 * fonctionnalité — pas un exemple synthétique, contrairement à [ReceiptAmountParserTest]. Les
 * particularités volontairement conservées (apostrophe perdue dans "Type d opération", deux
 * libellés sur une même ligne, "Type de Frais" contenant le mot "Frais" avant le vrai libellé
 * "Frais") sont exactement ce qui a guidé la conception du parser — à enrichir avec d'autres
 * exemples réels (Orange Money, Airtel Money...) dès qu'ils seront disponibles (voir Étape 10,
 * "Vérification finale").
 */
class ReceiptTransactionInfoParserTest {

    @Test
    fun `recu MyNITA reel - tous les champs`() {
        val info = ReceiptTransactionInfoParser.parse(MYNITA_RECEIPT_TEXT)

        assertEquals(1_000_000L, info.amountMinor) // 10 000 CFA
        assertEquals(30_000L, info.feeMinor) // 300 CFA, jamais confondu avec "Type de Frais"
        assertEquals(TransactionType.EXPENSE, info.transactionType) // "Débit"
        assertEquals("Transfert vers Ari Aoua", info.description)
        assertEquals("MYNITA1ABA77D905DF3", info.transactionReference)

        val expectedMillis = DatePeriods.toEpochMillis(LocalDate.of(2026, 8, 12), LocalTime.of(14, 13, 36))
        assertEquals(expectedMillis, info.dateTimeMillis)
    }

    @Test
    fun `frais jamais confondu avec le libelle Type de Frais`() {
        val text = "Type de Frais : fraisApars Frais : 300 CFA"

        assertEquals(30_000L, ReceiptTransactionInfoParser.parse(text).feeMinor)
    }

    @Test
    fun `credit detecte comme revenu`() {
        val text = "Type d'opération : Crédit"

        assertEquals(TransactionType.INCOME, ReceiptTransactionInfoParser.parse(text).transactionType)
    }

    @Test
    fun `aucune date retourne un instant nul`() {
        val text = "Montant : 5 000 FCFA"

        assertNull(ReceiptTransactionInfoParser.parse(text).dateTimeMillis)
    }

    @Test
    fun `date seule sans heure retourne quand meme un instant - minuit local`() {
        val text = "Date : 01/01/2026"

        val expectedMillis = DatePeriods.toEpochMillis(LocalDate.of(2026, 1, 1), LocalTime.MIDNIGHT)
        assertEquals(expectedMillis, ReceiptTransactionInfoParser.parse(text).dateTimeMillis)
    }

    @Test
    fun `aucune information retourne un resultat entierement vide`() {
        val info = ReceiptTransactionInfoParser.parse("Merci pour votre confiance !")

        assertNull(info.amountMinor)
        assertNull(info.feeMinor)
        assertNull(info.dateTimeMillis)
        assertNull(info.description)
        assertNull(info.transactionType)
        assertNull(info.transactionReference)
    }

    private companion object {
        val MYNITA_RECEIPT_TEXT = """
            Reçu : Envoi d'argent le 12/08/2026 14:13:36
            Code : MYNITA1ABA77D905DF3
            Expéditeur : Abdoul Kader Bachir
            Destinataire : Ari Aoua
            Montant : 10 000 CFA Ville : NIAMEY
            Type de Frais : fraisApars Frais : 300 CFA
            Type d opération : Débit
            Merci pour votre confiance !
        """.trimIndent()
    }
}
