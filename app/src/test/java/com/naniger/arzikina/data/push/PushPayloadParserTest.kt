package com.naniger.arzikina.data.push

import com.naniger.arzikina.domain.model.PushMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Format « data » v1 des notifications push (voir `server/api/push/PushPayload.php`) : tout
 * message mal formé, d'une autre version ou d'un type inconnu doit être ignoré sans erreur.
 */
class PushPayloadParserTest {

    private val uid = "11111111-1111-4111-8111-111111111111"
    private val eventId = "22222222-2222-4222-8222-222222222222"
    private val loanId = "0E8A2C1B-1234-4ABC-8DEF-001122334455"

    private val systemMessage = mapOf(
        "v" to "1",
        "type" to "SYSTEM_MESSAGE",
        "uid" to uid,
        "event_id" to eventId,
        "sent_at" to "1791000000000"
    )

    private val loanDue = systemMessage + mapOf("type" to "LOAN_DUE", "entity_type" to "loan", "entity_id" to loanId)

    @Test
    fun `message systeme valide`() {
        val message = PushPayloadParser.parse(systemMessage)
        assertNotNull(message)
        assertEquals(PushMessageType.SYSTEM_MESSAGE, message!!.type)
        assertEquals(uid, message.recipientServerUserId)
        assertNull(message.entityServerId)
        assertEquals(1791000000000L, message.sentAtMillis)
    }

    @Test
    fun `echeance de pret valide avec UUID normalise en minuscules`() {
        assertEquals(loanId.lowercase(), PushPayloadParser.parse(loanDue)?.entityServerId)
    }

    @Test
    fun `version absente ou inconnue ignoree`() {
        assertNull(PushPayloadParser.parse(systemMessage - "v"))
        assertNull(PushPayloadParser.parse(systemMessage + ("v" to "2")))
    }

    @Test
    fun `type inconnu ignore`() {
        assertNull(PushPayloadParser.parse(systemMessage + ("type" to "NEW_FEATURE")))
    }

    @Test
    fun `destinataire ou evenement invalides ignores`() {
        assertNull(PushPayloadParser.parse(systemMessage + ("uid" to "42")))
        assertNull(PushPayloadParser.parse(systemMessage - "uid"))
        assertNull(PushPayloadParser.parse(systemMessage - "event_id"))
    }

    @Test
    fun `entite incoherente avec le type ignoree`() {
        assertNull(PushPayloadParser.parse(loanDue - "entity_id"))
        assertNull(PushPayloadParser.parse(loanDue + ("entity_type" to "transaction")))
        // Identifiant local Room (Long) : jamais accepté, seul l'UUID serveur est valide.
        assertNull(PushPayloadParser.parse(loanDue + ("entity_id" to "12")))
        assertNull(PushPayloadParser.parse(systemMessage + mapOf("entity_type" to "loan", "entity_id" to loanId)))
    }

    @Test
    fun `date d envoi illisible toleree`() {
        assertEquals(0L, PushPayloadParser.parse(systemMessage + ("sent_at" to "abc"))?.sentAtMillis)
    }

    @Test
    fun `message vide ignore`() {
        assertNull(PushPayloadParser.parse(emptyMap()))
    }
}
