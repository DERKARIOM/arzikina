package com.naniger.arzikina.data.issuedreceipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.naniger.arzikina.domain.issuedreceipt.IssuerLogoPolicy
import com.naniger.arzikina.domain.issuedreceipt.LogoImportException
import com.naniger.arzikina.domain.issuedreceipt.LogoImportException.Reason
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Logo prêt à être stocké : [extension] vaut `png` (transparence conservée) ou `jpg`. */
class ProcessedLogo(val bytes: ByteArray, val extension: String)

/**
 * Transforme une image choisie par l'utilisateur en logo léger et sûr : lecture bornée (5 Mo),
 * type RÉEL vérifié (PNG/JPEG/WebP), garde-fou de dimensions, décodage sous-échantillonné (jamais
 * l'image complète en mémoire), rotation EXIF, redimensionnement à 512 px max SANS déformation.
 * Bloquant : à appeler hors du thread principal. Règles chiffrées : [IssuerLogoPolicy].
 */
@Singleton
class IssuerLogoProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** @throws LogoImportException */
    fun process(sourceUri: String): ProcessedLogo {
        val bytes = readBounded(Uri.parse(sourceUri))
        val mime = IssuerLogoPolicy.detectMimeType(bytes) ?: throw LogoImportException(Reason.UNSUPPORTED_TYPE)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) throw LogoImportException(Reason.UNREADABLE)
        if (width.toLong() * height > IssuerLogoPolicy.MAX_SOURCE_PIXELS) throw LogoImportException(Reason.TOO_LARGE)

        val options = BitmapFactory.Options().apply { inSampleSize = IssuerLogoPolicy.sampleSize(width, height) }
        val decoded = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (e: OutOfMemoryError) {
            throw LogoImportException(Reason.TOO_LARGE, e)
        } ?: throw LogoImportException(Reason.UNREADABLE)

        return try {
            val oriented = if (mime == "image/jpeg") applyOrientation(decoded, bytes) else decoded
            val (targetW, targetH) = IssuerLogoPolicy.targetSize(oriented.width, oriented.height)
            val scaled = if (targetW == oriented.width && targetH == oriented.height) {
                oriented
            } else {
                Bitmap.createScaledBitmap(oriented, targetW, targetH, true)
            }
            val keepAlpha = scaled.hasAlpha()
            val out = ByteArrayOutputStream()
            val format = if (keepAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            if (!scaled.compress(format, if (keepAlpha) 100 else JPEG_QUALITY, out)) {
                throw LogoImportException(Reason.UNREADABLE)
            }
            if (scaled !== decoded) scaled.recycle()
            if (oriented !== decoded && oriented !== scaled) oriented.recycle()
            ProcessedLogo(out.toByteArray(), if (keepAlpha) "png" else "jpg")
        } catch (e: OutOfMemoryError) {
            throw LogoImportException(Reason.TOO_LARGE, e)
        } finally {
            decoded.recycle()
        }
    }

    /** Lit au plus [IssuerLogoPolicy.MAX_SOURCE_BYTES] octets ; au-delà, refus (sans charger la suite). */
    private fun readBounded(uri: Uri): ByteArray {
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw LogoImportException(Reason.UNREADABLE)
            input.use { stream ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_SIZE)
                var total = 0
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > IssuerLogoPolicy.MAX_SOURCE_BYTES) throw LogoImportException(Reason.TOO_LARGE)
                    out.write(buffer, 0, read)
                }
                if (total == 0) throw LogoImportException(Reason.UNREADABLE)
                return out.toByteArray()
            }
        } catch (e: IOException) {
            throw LogoImportException(Reason.UNREADABLE, e)
        } catch (e: SecurityException) {
            throw LogoImportException(Reason.UNREADABLE, e)
        }
    }

    private fun applyOrientation(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = try {
            ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val BUFFER_SIZE = 8 * 1024
        const val JPEG_QUALITY = 90
    }
}
