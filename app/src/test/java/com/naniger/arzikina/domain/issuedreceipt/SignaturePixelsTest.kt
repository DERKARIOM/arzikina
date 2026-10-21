package com.naniger.arzikina.domain.issuedreceipt

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignaturePixelsTest {

    private fun gray(v: Int, alpha: Int = 255) = (alpha shl 24) or (v shl 16) or (v shl 8) or v
    private val white = gray(255)
    private val ink = gray(20)

    /** Image [w]x[h] blanche avec un bloc d'encre aux coordonnées données. */
    private fun image(w: Int, h: Int, paper: Int = 255, block: IntRange? = null, rows: IntRange? = null): IntArray {
        val px = IntArray(w * h) { gray(paper) }
        if (block != null && rows != null) for (y in rows) for (x in block) px[y * w + x] = ink
        return px
    }

    @Test
    fun whitePaperBecomesTransparentAndInkStaysOpaque() {
        val px = image(20, 10, block = 5..9, rows = 3..5)
        SignaturePixels.makeBackgroundTransparent(px)
        assertEquals(0, px[0] ushr 24)
        assertEquals(255, px[3 * 20 + 5] ushr 24)
    }

    @Test
    fun greyShadedPaperIsStillRemoved() {
        val px = image(30, 10, paper = 190, block = 10..14, rows = 2..6)
        SignaturePixels.makeBackgroundTransparent(px)
        assertEquals(0, px[0] ushr 24)
        assertEquals(255, px[2 * 30 + 10] ushr 24)
    }

    @Test
    fun colourOfInkIsPreserved() {
        val px = intArrayOf(0xFF0B1F4D.toInt(), white, white, white, white, white, white, white, white, white)
        SignaturePixels.makeBackgroundTransparent(px)
        assertEquals(0x0B1F4D, px[0] and 0xFFFFFF)
    }

    @Test
    fun existingTransparencyIsNeverMadeMoreOpaque() {
        val px = intArrayOf(gray(20, alpha = 100), white)
        SignaturePixels.makeBackgroundTransparent(px)
        assertTrue((px[0] ushr 24) <= 100)
    }

    @Test
    fun inkBoundsFindsTightRectangle() {
        val px = image(20, 10, block = 5..9, rows = 3..5)
        SignaturePixels.makeBackgroundTransparent(px)
        assertArrayEquals(intArrayOf(5, 3, 10, 6), SignaturePixels.inkBounds(px, 20, 10))
    }

    @Test
    fun emptyOrNoisyImageHasNoBounds() {
        val blank = image(20, 10)
        SignaturePixels.makeBackgroundTransparent(blank)
        assertNull(SignaturePixels.inkBounds(blank, 20, 10))
        val dust = image(20, 10, block = 4..5, rows = 4..4) // 2 pixels seulement
        SignaturePixels.makeBackgroundTransparent(dust)
        assertNull(SignaturePixels.inkBounds(dust, 20, 10))
    }

    @Test
    fun paddingStaysInsideTheImage() {
        assertArrayEquals(intArrayOf(0, 0, 8, 7), SignaturePixels.padded(intArrayOf(1, 2, 5, 4), 6, 8, 7))
        assertNotNull(SignaturePixels.padded(intArrayOf(0, 0, 1, 1), 6, 100, 100))
    }
}
