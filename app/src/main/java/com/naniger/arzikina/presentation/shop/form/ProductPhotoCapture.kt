package com.naniger.arzikina.presentation.shop.form

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.naniger.arzikina.util.Constants
import java.io.File

/**
 * Fichier TEMPORAIRE dans lequel l'appareil photo écrit la photo d'un produit (contrat `TakePicture`).
 * Cache privé de l'app (`cacheDir/camera_captures/`, déjà exposé par le FileProvider, voir
 * `res/xml/file_paths.xml`). Nom FIXE : chaque prise de vue écrase la précédente, rien ne s'accumule ;
 * la photo définitive est celle produite par `ProductImageStore` à l'enregistrement du produit.
 */
internal object ProductPhotoCapture {

    private const val DIRECTORY = "camera_captures"
    private const val FILE_NAME = "product_photo_capture.jpg"

    fun createUri(context: Context): Uri {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        return FileProvider.getUriForFile(
            context,
            context.packageName + Constants.FILE_PROVIDER_AUTHORITY_SUFFIX,
            File(directory, FILE_NAME)
        )
    }
}
