package com.naniger.arzikina.data.profile

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.naniger.arzikina.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stockage physique de la photo de profil dans le répertoire privé de l'application — cahier des
 * charges "Gestion de la photo de profil" : `files/profile_photos/`, jamais le stockage public,
 * jamais de permission de stockage demandée (inutile : `context.filesDir` est TOUJOURS accessible
 * sans permission, quelle que soit la version d'Android). Même principe et même structure que
 * [com.naniger.arzikina.data.receipts.ReceiptFileStorage] (photo optimisée plutôt que PDF), volontairement
 * DUPLIQUÉ plutôt que généralisé : les deux stockages ont des règles de nommage/extension différentes
 * et aucun appelant commun, généraliser maintenant ajouterait une abstraction sans bénéfice réel.
 *
 * Ne connaît JAMAIS [com.naniger.arzikina.data.local.entity.UserProfilePhotoEntity] ni Room (voir le
 * futur `ProfilePhotoRepository`, seul appelant prévu) : uniquement responsable des octets sur le
 * disque, séparation stricte des responsabilités.
 *
 * [com.naniger.arzikina.data.local.entity.UserProfilePhotoEntity.localPath] stocke le chemin RETOURNÉ
 * par [writeOptimizedImage] (relatif à [Context.getFilesDir]) — jamais un chemin absolu, jamais
 * l'URI `content://` temporaire d'origine (galerie/caméra), qui n'a aucune garantie de rester
 * lisible après un redémarrage de l'appareil.
 */
@Singleton
class ProfilePhotoFileStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Créé à la demande (jamais au démarrage de l'app), même principe que
     *  `ReceiptFileStorage.receiptsDirectory`. */
    private val profilePhotosDirectory: File
        get() = File(context.filesDir, PROFILE_PHOTOS_DIRECTORY_NAME).apply { if (!exists()) mkdirs() }

    /**
     * Écrit [bytes] (photo déjà recadrée/optimisée — voir la future étape "Optimisation de
     * l'image") dans un NOUVEAU fichier local. Nom physique généré (UUID, extension `.jpg` fixe) —
     * jamais dérivé d'un nom d'origine : élimine toute collision, cohérent avec le format JPEG
     * produit par l'optimisation. Ne supprime PAS l'éventuel ancien fichier : voir [deleteFile],
     * à appeler explicitement par l'appelant une fois la nouvelle ligne `user_profile_photos`
     * validée, pour ne jamais se retrouver sans fichier valide en cas d'échec entre les deux.
     */
    fun writeOptimizedImage(bytes: ByteArray): String {
        val physicalFileName = "${UUID.randomUUID()}.jpg"
        val destinationFile = File(profilePhotosDirectory, physicalFileName)
        destinationFile.writeBytes(bytes)
        return "$PROFILE_PHOTOS_DIRECTORY_NAME/$physicalFileName"
    }

    /** Résout un chemin relatif (voir [writeOptimizedImage]) vers le [File] réel sur le disque —
     *  jamais l'inverse, même principe que `ReceiptFileStorage.resolveFile`. */
    fun resolveFile(relativePath: String): File = File(context.filesDir, relativePath)

    /** `true` si un fichier existait réellement et a été supprimé — `false` sinon (jamais
     *  d'exception : un fichier déjà absent ne doit jamais faire échouer un remplacement/une
     *  suppression de photo). Appelé par le futur `ProfilePhotoRepository` après confirmation
     *  qu'aucune autre ligne ne référence plus l'ancien chemin, pour éviter tout fichier orphelin
     *  (cahier des charges, "vérifier qu'aucun doublon ou fichier orphelin n'est créé"). */
    fun deleteFile(relativePath: String): Boolean {
        val file = resolveFile(relativePath)
        return file.exists() && file.delete()
    }

    /**
     * URI `content://` sécurisée via [FileProvider] (voir `AndroidManifest.xml`,
     * `res/xml/file_paths.xml`) — seule façon autorisée d'afficher/passer la photo de profil à une
     * autre composante (ex. l'écran de recadrage, l'intent caméra) sans exposer directement un
     * chemin de fichier privé.
     */
    fun contentUriFor(relativePath: String): Uri =
        FileProvider.getUriForFile(
            context,
            context.packageName + Constants.FILE_PROVIDER_AUTHORITY_SUFFIX,
            resolveFile(relativePath)
        )

    private companion object {
        const val PROFILE_PHOTOS_DIRECTORY_NAME = "profile_photos"
    }
}
