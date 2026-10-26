package com.naniger.arzikina.domain.shop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductImagePolicyTest {

    @Test
    fun `une grande image est ramenee a 1024 px en gardant les proportions`() {
        val (w, h) = ProductImagePolicy.targetSize(4000, 3000)
        assertEquals(1024, w)
        assertEquals(768, h)
    }

    @Test
    fun `une petite image n est jamais agrandie`() {
        assertEquals(300 to 200, ProductImagePolicy.targetSize(300, 200))
    }

    @Test
    fun `le sous echantillonnage reste une puissance de deux`() {
        val s = ProductImagePolicy.sampleSize(4000, 3000)
        assertTrue(s >= 1 && (s and (s - 1)) == 0)
    }

    @Test
    fun `le type est detecte par le contenu et non par l extension`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10, 0x4A, 0x46, 0x49, 0x46, 0, 1)
        assertEquals("image/jpeg", ProductImagePolicy.detectMimeType(jpeg))
        assertNull(ProductImagePolicy.detectMimeType("<html>pas une image</html>".toByteArray()))
    }

    @Test
    fun `seuls les noms generes par l appli sont acceptes`() {
        assertTrue(ProductImagePolicy.isSafeFileName("3f2a9c1e-7b4d-4e0a-9c11-0a1b2c3d4e5f.jpg"))
        assertFalse(ProductImagePolicy.isSafeFileName("../secret.jpg"))
        assertFalse(ProductImagePolicy.isSafeFileName("a/b.jpg"))
        assertFalse(ProductImagePolicy.isSafeFileName("photo.png"))
        assertFalse(ProductImagePolicy.isSafeFileName(".jpg"))
        assertFalse(ProductImagePolicy.isSafeFileName(""))
    }

    @Test
    fun `seul un jpeg raisonnable est restaure depuis une sauvegarde`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10, 0x4A, 0x46)
        assertTrue(ProductImagePolicy.isRestorableImage(jpeg))
        assertFalse(ProductImagePolicy.isRestorableImage(ByteArray(0)))
        assertFalse(ProductImagePolicy.isRestorableImage("pas une image".toByteArray()))
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        assertFalse(ProductImagePolicy.isRestorableImage(png))
        val tooBig = jpeg + ByteArray(ProductImagePolicy.MAX_BACKUP_IMAGE_BYTES)
        assertFalse(ProductImagePolicy.isRestorableImage(tooBig))
    }
}
