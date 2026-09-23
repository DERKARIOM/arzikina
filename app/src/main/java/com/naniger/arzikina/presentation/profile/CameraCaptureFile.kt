package com.naniger.arzikina.presentation.profile

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.naniger.arzikina.util.Constants
import java.io.File

/**
 * Fichier TEMPORAIRE dans lequel l'application appareil photo écrit la photo prise pour le profil
 * (contrat `ActivityResultContracts.TakePicture`, voir [ProfileFragment]).
 *
 * - Cache privé de l'app (`cacheDir/camera_captures/`), exposé via le FileProvider existant (voir
 *   `res/xml/file_paths.xml`) : l'app appareil photo n'obtient un droit d'écriture que sur CETTE URI.
 * - Nom FIXE : chaque prise de vue écrase la précédente, aucun fichier ne s'accumule. Ce fichier n'est
 *   qu'une étape : la photo définitive est celle produite par le recadrage, puis enregistrée par
 *   `ProfilePhotoFileStorage` après validation.
 */
internal object CameraCaptureFile {

    private const val DIRECTORY = "camera_captures"
    private const val FILE_NAME = "profile_photo_capture.jpg"

    fun createUri(context: Context): Uri {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        return FileProvider.getUriForFile(
            context,
            context.packageName + Constants.FILE_PROVIDER_AUTHORITY_SUFFIX,
            File(directory, FILE_NAME)
        )
    }
}
