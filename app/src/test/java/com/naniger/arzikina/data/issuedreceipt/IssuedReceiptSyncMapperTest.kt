package com.naniger.arzikina.data.issuedreceipt

import com.naniger.arzikina.data.local.entity.IssuedReceiptEntity
import com.naniger.arzikina.data.local.entity.IssuerProfileEntity
import com.naniger.arzikina.data.remote.dto.IssuedReceiptServerStateDto
import com.naniger.arzikina.data.remote.dto.IssuedReceiptSyncPayload
import com.naniger.arzikina.data.remote.dto.IssuerProfileServerStateDto
import com.naniger.arzikina.domain.model.IssuerSnapshot
import com.naniger.arzikina.domain.model.SyncOperation
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IssuedReceiptSyncMapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val issuerJson = IssuedReceiptJson.encodeIssuer(
        IssuerSnapshot("Boutique Awa", "Niamey", "+22790000000", "awa@example.com", null, "logo-1.png")
    )

    private fun receipt(
        number: String = "Recu-2026-001",
        updatedAt: Long = 20L,
        pdf: String? = "Recu-2026-001.pdf",
        version: Int = 3
    ) = IssuedReceiptEntity(
        id = 7L, userId = 1L, number = number, issuedAt = 1_000L, customerName = "Moussa", customerPhone = null,
        description = "Riz 50 kg", amountMinor = 2_500_000L, currencyCode = "XOF", paymentMethod = "CASH",
        transactionReference = null, notes = null, issuedByName = "Awa", issuedByTitle = null,
        pricingJson = """{"lines":[],"discountType":"none","discountValue":0,"taxBasisPoints":0}""",
        issuerJson = issuerJson, pdfFileName = pdf, createdAt = 10L, updatedAt = updatedAt,
        syncId = "sync-1", deletedAt = null, version = version
    )

    private fun state(number: String = "Recu-2026-001", updatedAt: Long = 20L, deletedAt: Long? = null) =
        IssuedReceiptServerStateDto(
            id = "sync-1", number = number, issuedAt = 1_000L, customerName = "Moussa", description = "Riz 50 kg",
            amountMinor = 2_500_000L, currencyCode = "XOF", paymentMethod = "CASH",
            issuerBusinessName = "Boutique Awa", issuerAddress = "Niamey", issuerPhone = "+22790000000",
            issuerEmail = "awa@example.com", issuerLogoFileName = "logo-1.png",
            createdAt = 10L, updatedAt = updatedAt, deletedAt = deletedAt, version = 4
        )

    @Test
    fun `profile sync id is deterministic per server user and differs between users`() {
        val a = IssuedReceiptSyncMapper.profileSyncId("42")
        assertEquals(a, IssuedReceiptSyncMapper.profileSyncId("42"))
        assertNotEquals(a, IssuedReceiptSyncMapper.profileSyncId("43"))
        assertTrue(a.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    @Test
    fun `create has no base version, update and delete carry the known version`() {
        assertNull(IssuedReceiptSyncMapper.toPayload(receipt(), "sync-1", SyncOperation.CREATE).baseVersion)
        assertEquals(3, IssuedReceiptSyncMapper.toPayload(receipt(), "sync-1", SyncOperation.UPDATE).baseVersion)
        assertEquals(3, IssuedReceiptSyncMapper.toPayload(receipt(), "sync-1", SyncOperation.DELETE).baseVersion)
    }

    @Test
    fun `receipt payload flattens the issuer snapshot and keeps the pricing json verbatim`() {
        val payload = IssuedReceiptSyncMapper.toPayload(receipt(), "sync-1", SyncOperation.CREATE)
        assertEquals("Boutique Awa", payload.issuerBusinessName)
        assertEquals("logo-1.png", payload.issuerLogoFileName)
        assertEquals(receipt().pricingJson, payload.pricingJson)
        assertEquals(listOf("logo-1.png"), IssuedReceiptSyncMapper.logoNames(payload))
    }

    @Test
    fun `payload serializes absent optional values as explicit nulls so they can be cleared`() {
        val text = json.encodeToString(
            IssuedReceiptSyncPayload.serializer(),
            IssuedReceiptSyncMapper.toPayload(receipt(), "sync-1", SyncOperation.UPDATE)
        )
        // explicitNulls par défaut : sans la clé, le serveur « conserve » l'ancienne valeur.
        assertTrue(text.contains("\"customerPhone\":null"))
        assertTrue(text.contains("\"notes\":null"))
    }

    @Test
    fun `state to entity round trips through the payload`() {
        val entity = IssuedReceiptSyncMapper.toEntity(state(), userId = 1L, local = null)
        val payload = IssuedReceiptSyncMapper.toPayload(entity, "sync-1", SyncOperation.UPDATE)
        assertEquals("Boutique Awa", payload.issuerBusinessName)
        assertEquals("awa@example.com", payload.issuerEmail)
        assertEquals(4, entity.version)
        assertEquals("sync-1", entity.syncId)
    }

    @Test
    fun `entity keeps the local row id and the pdf cache only when nothing changed`() {
        val unchanged = IssuedReceiptSyncMapper.toEntity(state(), 1L, receipt())
        assertEquals(7L, unchanged.id)
        assertEquals("Recu-2026-001.pdf", unchanged.pdfFileName)

        val newer = IssuedReceiptSyncMapper.toEntity(state(updatedAt = 99L), 1L, receipt())
        assertNull(newer.pdfFileName)

        val renumbered = IssuedReceiptSyncMapper.toEntity(state(), 1L, receipt(), number = "Recu-2026-001-2")
        assertEquals("Recu-2026-001-2", renumbered.number)
        assertNull(renumbered.pdfFileName)
    }

    @Test
    fun `deleted state is kept as a tombstone`() {
        assertEquals(55L, IssuedReceiptSyncMapper.toEntity(state(deletedAt = 55L), 1L, receipt()).deletedAt)
    }

    @Test
    fun `profile state maps onto the local row`() {
        val local = IssuerProfileEntity(
            id = 3L, userId = 1L, businessName = "Old", address = "x", phone = "1", email = null, taxId = null,
            logoFileName = null, defaultIssuedByName = null, defaultIssuedByTitle = null,
            createdAt = 1L, updatedAt = 2L
        )
        val server = IssuerProfileServerStateDto(
            id = "p", businessName = "New", address = "Niamey", phone = "2", logoFileName = "l.png",
            createdAt = 1L, updatedAt = 9L, version = 5
        )
        val merged = IssuedReceiptSyncMapper.toEntity(server, 1L, local)
        assertEquals(3L, merged.id)
        assertEquals("New", merged.businessName)
        assertEquals("p", merged.syncId)
        assertEquals(5, merged.version)
        assertNull(merged.email)
    }

    @Test
    fun `permanent logo failures are 4xx except auth, timeout and throttling`() {
        assertTrue(LogoSyncPolicy.isPermanentFailure(400))
        assertTrue(LogoSyncPolicy.isPermanentFailure(404))
        assertTrue(LogoSyncPolicy.isPermanentFailure(409))
        assertTrue(LogoSyncPolicy.isPermanentFailure(413))
        assertFalse(LogoSyncPolicy.isPermanentFailure(401))
        assertFalse(LogoSyncPolicy.isPermanentFailure(403))
        assertFalse(LogoSyncPolicy.isPermanentFailure(408))
        assertFalse(LogoSyncPolicy.isPermanentFailure(429))
        assertFalse(LogoSyncPolicy.isPermanentFailure(500))
        assertFalse(LogoSyncPolicy.isPermanentFailure(503))
    }

    @Test
    fun `signature travels with the profile and with a signed receipt`() {
        val profile = IssuerProfileEntity(
            id = 3L, userId = 1L, businessName = "Boutique", address = "x", phone = "1", email = null, taxId = null,
            logoFileName = "logo-1.png", defaultIssuedByName = null, defaultIssuedByTitle = null,
            createdAt = 1L, updatedAt = 2L, signatureFileName = "sig-1.png"
        )
        val payload = IssuedReceiptSyncMapper.toPayload(profile, "p", SyncOperation.UPDATE)
        assertEquals("sig-1.png", payload.signatureFileName)
        assertEquals(listOf("logo-1.png", "sig-1.png"), IssuedReceiptSyncMapper.logoNames(payload))

        val signedIssuer = IssuedReceiptJson.encodeIssuer(
            IssuerSnapshot("Boutique", "Niamey", "1", null, null, "logo-1.png", "sig-1.png")
        )
        val signed = IssuedReceiptSyncMapper.toPayload(receipt().copy(issuerJson = signedIssuer), "sync-1", SyncOperation.UPDATE)
        assertEquals("sig-1.png", signed.issuerSignatureFileName)
        assertEquals(listOf("logo-1.png", "sig-1.png"), IssuedReceiptSyncMapper.logoNames(signed))
    }

    @Test
    fun `an unsigned receipt has no signature and server state round-trips it`() {
        val unsigned = IssuedReceiptSyncMapper.toPayload(receipt(), "sync-1", SyncOperation.UPDATE)
        assertNull(unsigned.issuerSignatureFileName)
        assertEquals(listOf("logo-1.png"), IssuedReceiptSyncMapper.logoNames(unsigned))

        val fromServer = IssuedReceiptSyncMapper.toEntity(state().copy(issuerSignatureFileName = "sig-2.png"), 1L, null)
        assertEquals("sig-2.png", IssuedReceiptJson.decodeIssuer(fromServer.issuerJson).signatureFileName)
    }

    @Test
    fun `an issuer stored before signatures existed still decodes as unsigned`() {
        val legacy = "{\"businessName\":\"Boutique\",\"address\":\"Niamey\",\"phone\":\"1\",\"logoFileName\":\"logo-1.png\"}"
        assertNull(IssuedReceiptJson.decodeIssuer(legacy).signatureFileName)
    }
}
