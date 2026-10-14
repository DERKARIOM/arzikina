package com.naniger.arzikina.util.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.naniger.arzikina.domain.web.WebConsole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeGeneratorTest {

    private fun decode(content: String, size: Int): String {
        val matrix = QrCodeGenerator.encode(content, size)
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix[i % matrix.width, i / matrix.width]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val source = RGBLuminanceSource(matrix.width, matrix.height, pixels)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    @Test
    fun `le QR de la console se relit exactement`() {
        assertEquals(WebConsole.LOGIN_URL, decode(WebConsole.LOGIN_URL, 512))
    }

    @Test
    fun `la matrice est carree et contient des modules sombres`() {
        val matrix = QrCodeGenerator.encode(WebConsole.LOGIN_URL, 300)
        assertEquals(matrix.width, matrix.height)
        assertTrue((0 until matrix.width).any { x -> (0 until matrix.height).any { y -> matrix[x, y] } })
    }

    @Test
    fun `le lien de la console est en HTTPS vers la page de connexion`() {
        assertTrue(WebConsole.LOGIN_URL.startsWith("https://"))
        assertTrue(WebConsole.LOGIN_URL.endsWith("/connexion"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un contenu vide est refuse`() {
        QrCodeGenerator.encode("", 200)
    }
}
