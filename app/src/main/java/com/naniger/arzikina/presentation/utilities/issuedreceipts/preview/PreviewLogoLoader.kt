package com.naniger.arzikina.presentation.utilities.issuedreceipts.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.naniger.arzikina.domain.issuedreceipt.IssuerLogoPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Origine du logo à afficher dans l'aperçu : image choisie (pas encore enregistrée) ou fichier mémorisé. */
sealed interface PreviewLogoSource {
    data class Picked(val uri: String) : PreviewLogoSource
    data class Stored(val file: File) : PreviewLogoSource
}

/**
 * Charge un logo pour l'aperçu, sous-échantillonné (jamais l'image entière en mémoire). Retourne
 * `null` si illisible : l'aperçu s'affiche alors sans logo. Le vrai traitement (rotation EXIF, 512 px)
 * se fait à l'enregistrement ; ici la précision d'un aperçu suffit.
 */
object PreviewLogoLoader {

    suspend fun load(context: Context, source: PreviewLogoSource): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            when (source) {
                is PreviewLogoSource.Stored -> decodeFile(source.file)
                is PreviewLogoSource.Picked -> decodeUri(context, Uri.parse(source.uri))
            }
        }.getOrNull()
    }

    private fun decodeFile(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        return BitmapFactory.decodeFile(file.path, sampled(bounds))
    }

    private fun decodeUri(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, sampled(bounds)) }
    }

    private fun sampled(bounds: BitmapFactory.Options) = BitmapFactory.Options().apply {
        inSampleSize = IssuerLogoPolicy.sampleSize(bounds.outWidth, bounds.outHeight)
    }
}
