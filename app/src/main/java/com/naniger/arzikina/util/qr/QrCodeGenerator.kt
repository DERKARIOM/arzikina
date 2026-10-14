package com.naniger.arzikina.util.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Génère un QR code avec ZXing (bibliothèque de référence, Java pur, sans service Google).
 *
 * Séparé en deux étapes : [encode] (pur, testable en JVM — voir `QrCodeGeneratorTest`, qui relit
 * le code avec le décodeur ZXing) et [toBitmap] (dessin Android). Le QR est toujours noir sur blanc,
 * quel que soit le thème : les lecteurs exigent un contraste noir/blanc, un QR « clair sur sombre »
 * est mal lu par beaucoup d'applications.
 *
 * À appeler hors du thread principal pour de grandes tailles (voir `WebAppViewModel`).
 */
object QrCodeGenerator {

    /** Marge de silence en modules (4 est la norme ; l'écran ajoute en plus son propre cadre blanc). */
    private const val QUIET_ZONE_MODULES = 2

    fun encode(content: String, sizePx: Int): BitMatrix {
        require(content.isNotEmpty()) { "Contenu vide" }
        require(sizePx > 0) { "Taille invalide" }
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to QUIET_ZONE_MODULES
        )
        return QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    }

    fun toBitmap(matrix: BitMatrix): Bitmap {
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    fun generate(content: String, sizePx: Int): Bitmap = toBitmap(encode(content, sizePx))
}
