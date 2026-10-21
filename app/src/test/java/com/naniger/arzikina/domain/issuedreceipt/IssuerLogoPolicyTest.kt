package com.naniger.arzikina.domain.issuedreceipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IssuerLogoPolicyTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test fun smallImageIsNeverEnlarged() {
        assertEquals(200 to 100, IssuerLogoPolicy.targetSize(200, 100))
    }

    @Test fun largeLandscapeKeepsRatio() {
        assertEquals(512 to 256, IssuerLogoPolicy.targetSize(2000, 1000))
    }

    @Test fun largePortraitKeepsRatio() {
        assertEquals(256 to 512, IssuerLogoPolicy.targetSize(1000, 2000))
    }

    @Test fun extremeRatioKeepsAtLeastOnePixel() {
        assertEquals(512 to 1, IssuerLogoPolicy.targetSize(100_000, 10))
    }

    @Test fun sampleSizeKeepsBothSidesAtLeastTarget() {
        assertEquals(1, IssuerLogoPolicy.sampleSize(600, 600))
        assertEquals(4, IssuerLogoPolicy.sampleSize(2048, 2048))
        // Image étroite : le petit côté (600) limite le sous-échantillonnage à 1.
        assertEquals(1, IssuerLogoPolicy.sampleSize(8000, 600))
    }

    @Test fun detectsRealTypes() {
        assertEquals("image/png", IssuerLogoPolicy.detectMimeType(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0)))
        assertEquals("image/jpeg", IssuerLogoPolicy.detectMimeType(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertEquals(
            "image/webp",
            IssuerLogoPolicy.detectMimeType(bytes('R'.code, 'I'.code, 'F'.code, 'F'.code, 0, 0, 0, 0, 'W'.code, 'E'.code, 'B'.code, 'P'.code))
        )
    }

    @Test fun rejectsOtherContent() {
        assertNull(IssuerLogoPolicy.detectMimeType("%PDF-1.7".toByteArray()))
        assertNull(IssuerLogoPolicy.detectMimeType("GIF89a".toByteArray()))
        assertNull(IssuerLogoPolicy.detectMimeType(ByteArray(0)))
        assertNull(IssuerLogoPolicy.detectMimeType(bytes(0x89, 0x50)))
    }

    @Test fun safeFileNames() {
        assertTrue(IssuerLogoPolicy.isSafeFileName("3f2a9c1e-77aa-4d0b-8c11-0a5b2d9e4f10.png"))
        assertTrue(IssuerLogoPolicy.isSafeFileName("abc.jpg"))
        assertFalse(IssuerLogoPolicy.isSafeFileName("../secret.png"))
        assertFalse(IssuerLogoPolicy.isSafeFileName("a/b.png"))
        assertFalse(IssuerLogoPolicy.isSafeFileName("logo.gif"))
        assertFalse(IssuerLogoPolicy.isSafeFileName(""))
    }

    @Test
    fun `synced logo must match its name, type and size`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)
        val jpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0)
        assertTrue(IssuerLogoPolicy.isValidSyncedLogo("abc-123.png", png))
        assertTrue(IssuerLogoPolicy.isValidSyncedLogo("abc-123.jpg", jpg))
        assertFalse(IssuerLogoPolicy.isValidSyncedLogo("abc-123.jpg", png))
        assertFalse(IssuerLogoPolicy.isValidSyncedLogo("abc-123.png", jpg))
        assertFalse(IssuerLogoPolicy.isValidSyncedLogo("../abc.png", png))
        assertFalse(IssuerLogoPolicy.isValidSyncedLogo("abc.png", ByteArray(0)))
        assertFalse(IssuerLogoPolicy.isValidSyncedLogo("abc.png", png + ByteArray(IssuerLogoPolicy.MAX_SYNCED_BYTES)))
        assertFalse(IssuerLogoPolicy.isValidSyncedLogo("abc.png", "<svg/>".toByteArray()))
    }
}
