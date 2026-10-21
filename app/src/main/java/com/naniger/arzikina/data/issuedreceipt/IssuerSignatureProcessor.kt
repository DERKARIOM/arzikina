package com.naniger.arzikina.data.issuedreceipt

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.naniger.arzikina.domain.issuedreceipt.IssuerLogoPolicy
import com.naniger.arzikina.domain.issuedreceipt.LogoImportException
import com.naniger.arzikina.domain.issuedreceipt.LogoImportException.Reason
import com.naniger.arzikina.domain.issuedreceipt.SignatureImage
import com.naniger.arzikina.domain.issuedreceipt.SignaturePixels
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fabrique une signature propre à partir d'une photo/image importée ou d'un tracé à main levée :
 * 1) lecture bornée et type RÉEL vérifié (même garde-fous que le logo, via [IssuerLogoProcessor]) ;
 * 2) décodage sous-échantillonné, rotation EXIF ;
 * 3) fond blanc rendu transparent (sauf si l'image a déjà sa propre transparence) ;
 * 4) recadrage sur l'encre, avec une petite marge ;
 * 5) réduction à [IssuerLogoPolicy.MAX_DIMENSION_PX] max (jamais d'agrandissement), export PNG.
 * Le résultat est toujours un PNG transparent léger : accepté tel quel par la synchronisation.
 * Bloquant : à appeler hors du thread principal. Les calculs de pixels vivent dans [SignaturePixels].
 */
@Singleton
class IssuerSignatureProcessor @Inject constructor(
    private val logoProcessor: IssuerLogoProcessor
) {

    /** Image choisie par l'utilisateur (fond blanc rendu transparent). @throws LogoImportException */
    fun fromUri(sourceUri: String): SignatureImage = decodeAndFinish(logoProcessor.readSourceBytes(sourceUri), importedPhoto = true)

    /** Tracé dessiné à l'écran, exporté en PNG transparent par le pad. @throws LogoImportException */
    fun fromDrawnPng(png: ByteArray): SignatureImage {
        if (png.isEmpty()) throw LogoImportException(Reason.UNREADABLE)
        return decodeAndFinish(png, importedPhoto = false)
    }

    private fun decodeAndFinish(bytes: ByteArray, importedPhoto: Boolean): SignatureImage {
        val mime = IssuerLogoPolicy.detectMimeType(bytes) ?: throw LogoImportException(Reason.UNSUPPORTED_TYPE)
        if (!importedPhoto && mime != "image/png") throw LogoImportException(Reason.UNSUPPORTED_TYPE)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw LogoImportException(Reason.UNREADABLE)
        if (bounds.outWidth.toLong() * bounds.outHeight > IssuerLogoPolicy.MAX_SOURCE_PIXELS) throw LogoImportException(Reason.TOO_LARGE)

        val options = BitmapFactory.Options().apply {
            inSampleSize = IssuerLogoPolicy.sampleSize(bounds.outWidth, bounds.outHeight)
            inMutable = true
        }
        val decoded = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (e: OutOfMemoryError) {
            throw LogoImportException(Reason.TOO_LARGE, e)
        } ?: throw LogoImportException(Reason.UNREADABLE)

        var oriented: Bitmap? = null
        var cropped: Bitmap? = null
        var scaled: Bitmap? = null
        try {
            oriented = if (mime == "image/jpeg") logoProcessor.applyOrientation(decoded, bytes) else decoded
            val width = oriented.width
            val height = oriented.height
            val pixels = IntArray(width * height)
            oriented.getPixels(pixels, 0, width, 0, 0, width, height)
            if (importedPhoto && !oriented.hasAlpha()) SignaturePixels.makeBackgroundTransparent(pixels)

            val ink = SignaturePixels.inkBounds(pixels, width, height) ?: throw LogoImportException(Reason.EMPTY)
            val box = SignaturePixels.padded(ink, PADDING_PX, width, height)
            val boxW = box[2] - box[0]
            val boxH = box[3] - box[1]
            cropped = Bitmap.createBitmap(boxW, boxH, Bitmap.Config.ARGB_8888)
            cropped.setPixels(pixels, box[1] * width + box[0], width, 0, 0, boxW, boxH)

            val (targetW, targetH) = IssuerLogoPolicy.targetSize(boxW, boxH)
            scaled = if (targetW == boxW && targetH == boxH) cropped else Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
            val out = ByteArrayOutputStream()
            if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, out)) throw LogoImportException(Reason.UNREADABLE)
            return SignatureImage(out.toByteArray())
        } catch (e: OutOfMemoryError) {
            throw LogoImportException(Reason.TOO_LARGE, e)
        } finally {
            if (scaled != null && scaled !== cropped) scaled.recycle()
            cropped?.recycle()
            if (oriented != null && oriented !== decoded) oriented.recycle()
            decoded.recycle()
        }
    }

    private companion object {
        /** Marge autour de l'encre : évite un trait coupé net au bord du PNG. */
        const val PADDING_PX = 6
    }
}
