package com.naniger.arzikina.domain.shop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductImageSyncPolicyTest {

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)

    @Test
    fun `nom serveur sur est adopte`() {
        assertEquals("b.jpg", ProductImagePolicy.resolveReceivedName("b.jpg", "a.jpg", serverAuthoritative = false))
        assertEquals("b.jpg", ProductImagePolicy.resolveReceivedName("b.jpg", null, serverAuthoritative = true))
    }

    @Test
    fun `nom serveur dangereux est ignore et la photo locale conservee`() {
        assertEquals("a.jpg", ProductImagePolicy.resolveReceivedName("../x.jpg", "a.jpg", serverAuthoritative = true))
        assertEquals("a.jpg", ProductImagePolicy.resolveReceivedName("x.png", "a.jpg", serverAuthoritative = false))
    }

    @Test
    fun `sans nom serveur la photo locale n est retiree qu apres le rattrapage`() {
        assertEquals("a.jpg", ProductImagePolicy.resolveReceivedName(null, "a.jpg", serverAuthoritative = false))
        assertNull(ProductImagePolicy.resolveReceivedName(null, "a.jpg", serverAuthoritative = true))
        assertNull(ProductImagePolicy.resolveReceivedName(null, null, serverAuthoritative = true))
    }

    @Test
    fun `photo recue valide seulement si nom sur, JPEG reel et taille raisonnable`() {
        assertTrue(ProductImagePolicy.isValidSyncedImage("a.jpg", jpeg))
        assertFalse(ProductImagePolicy.isValidSyncedImage("a.jpg", png))
        assertFalse(ProductImagePolicy.isValidSyncedImage("a.jpg", ByteArray(0)))
        assertFalse(ProductImagePolicy.isValidSyncedImage("../a.jpg", jpeg))
        assertFalse(ProductImagePolicy.isValidSyncedImage("a.png", jpeg))
        val tooBig = jpeg + ByteArray(ProductImagePolicy.MAX_SYNC_BYTES)
        assertFalse(ProductImagePolicy.isValidSyncedImage("a.jpg", tooBig))
    }
}
