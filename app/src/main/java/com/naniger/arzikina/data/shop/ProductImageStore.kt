package com.naniger.arzikina.data.shop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import com.naniger.arzikina.data.issuedreceipt.IssuerLogoProcessor
import com.naniger.arzikina.domain.issuedreceipt.LogoImportException
import com.naniger.arzikina.domain.shop.ProductImageException
import com.naniger.arzikina.domain.shop.ProductImageException.Reason
import com.naniger.arzikina.domain.shop.ProductImageFiles
import com.naniger.arzikina.domain.shop.ProductImagePolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Import et stockage des photos de produit dans le répertoire PRIVÉ de l'application
 * (`files/product_images/`) : aucune permission, jamais lisible par une autre application.
 *
 * Import : lecture bornée (5 Mo) et rotation EXIF partagées avec les logos ([IssuerLogoProcessor]),
 * type RÉEL vérifié (PNG/JPEG/WebP), garde-fou de dimensions, décodage sous-échantillonné (jamais
 * l'image complète en mémoire), redimensionnement à 1024 px max SANS déformation, JPEG qualité 85
 * (une image transparente est posée sur fond blanc). Bloquant : à appeler hors du thread principal.
 * Les noms de fichier sont générés (UUID) et validés avant tout accès (anti-traversée de répertoire).
 */
@Singleton
class ProductImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val processor: IssuerLogoProcessor
) : ProductImageFiles {

    private val directory: File
        get() = File(context.filesDir, DIRECTORY_NAME).apply { if (!exists()) mkdirs() }

    /** @return le nom du NOUVEAU fichier écrit (à enregistrer en base). @throws ProductImageException */
    fun import(sourceUri: String): String {
        val bytes = try {
            processor.readSourceBytes(sourceUri)
        } catch (e: LogoImportException) {
            throw ProductImageException(e.reason.toImageReason(), e)
        }
        if (ProductImagePolicy.detectMimeType(bytes) == null) throw ProductImageException(Reason.UNSUPPORTED_TYPE)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) throw ProductImageException(Reason.UNREADABLE)
        if (width.toLong() * height > ProductImagePolicy.MAX_SOURCE_PIXELS) throw ProductImageException(Reason.TOO_LARGE)

        val options = BitmapFactory.Options().apply { inSampleSize = ProductImagePolicy.sampleSize(width, height) }
        val decoded = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (e: OutOfMemoryError) {
            throw ProductImageException(Reason.TOO_LARGE, e)
        } ?: throw ProductImageException(Reason.UNREADABLE)

        try {
            val oriented = processor.applyOrientation(decoded, bytes)
            val (targetW, targetH) = ProductImagePolicy.targetSize(oriented.width, oriented.height)
            val scaled = if (targetW == oriented.width && targetH == oriented.height) {
                oriented
            } else {
                Bitmap.createScaledBitmap(oriented, targetW, targetH, true)
            }
            val opaque = flattenOnWhite(scaled)
            val out = ByteArrayOutputStream()
            if (!opaque.compress(Bitmap.CompressFormat.JPEG, ProductImagePolicy.JPEG_QUALITY, out)) {
                throw ProductImageException(Reason.UNREADABLE)
            }
            if (opaque !== scaled) opaque.recycle()
            if (scaled !== oriented) scaled.recycle()
            if (oriented !== decoded) oriented.recycle()
            return write(out.toByteArray())
        } catch (e: OutOfMemoryError) {
            throw ProductImageException(Reason.TOO_LARGE, e)
        } finally {
            decoded.recycle()
        }
    }

    override fun resolve(fileName: String?): File? {
        if (fileName == null || !ProductImagePolicy.isSafeFileName(fileName)) return null
        return File(directory, fileName).takeIf { it.isFile }
    }

    /** Supprime le fichier s'il existe ; ne lève jamais (un fichier déjà absent n'est pas une erreur). */
    fun delete(fileName: String): Boolean {
        if (!ProductImagePolicy.isSafeFileName(fileName)) return false
        return File(directory, fileName).let { it.exists() && it.delete() }
    }

    /** Écriture atomique (fichier temporaire puis renommage) : jamais de photo tronquée. */
    private fun write(bytes: ByteArray): String {
        val fileName = "${UUID.randomUUID()}.jpg"
        val target = File(directory, fileName)
        val temp = File(directory, "$fileName.part")
        try {
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) throw ProductImageException(Reason.UNREADABLE)
        } finally {
            if (temp.exists()) temp.delete()
        }
        return fileName
    }

    /** Le JPEG n'a pas de transparence : une image à fond transparent est posée sur du blanc. */
    private fun flattenOnWhite(source: Bitmap): Bitmap {
        if (!source.hasAlpha()) return source
        val flat = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, 0f, 0f, null)
        }
        return flat
    }

    private fun LogoImportException.Reason.toImageReason(): Reason = when (this) {
        LogoImportException.Reason.UNSUPPORTED_TYPE -> Reason.UNSUPPORTED_TYPE
        LogoImportException.Reason.TOO_LARGE -> Reason.TOO_LARGE
        LogoImportException.Reason.UNREADABLE, LogoImportException.Reason.EMPTY -> Reason.UNREADABLE
    }

    private companion object {
        const val DIRECTORY_NAME = "product_images"
    }
}
