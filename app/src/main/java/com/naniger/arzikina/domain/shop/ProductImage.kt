package com.naniger.arzikina.domain.shop

import com.naniger.arzikina.domain.issuedreceipt.IssuerLogoPolicy
import java.io.File

/** Ce que l'utilisateur a décidé pour la photo d'un produit lors de l'enregistrement. */
sealed interface ProductImageChange {
    /** Ne rien changer (création : pas de photo). */
    data object Keep : ProductImageChange

    /** Retirer la photo existante. */
    data object Remove : ProductImageChange

    /** Remplacer par l'image située à [sourceUri] (galerie ou appareil photo), traitée à l'enregistrement. */
    data class Replace(val sourceUri: String) : ProductImageChange
}

/** Échec d'import d'une photo de produit ; [reason] est traduit en message FR/EN par la présentation. */
class ProductImageException(val reason: Reason, cause: Throwable? = null) :
    Exception("Import de la photo refusé : $reason", cause) {

    enum class Reason { UNSUPPORTED_TYPE, TOO_LARGE, UNREADABLE }
}

/** Résolution d'un nom de fichier de photo en fichier du stockage privé de l'application. */
interface ProductImageFiles {
    /** `null` si [fileName] est `null`, dangereux (traversée de répertoire) ou si le fichier n'existe plus. */
    fun resolve(fileName: String?): File?
}

/**
 * Règles des photos de produit (logique pure, testée). Les calculs de dimensions et la détection du
 * type réel sont partagés avec les logos d'émetteur ([IssuerLogoPolicy]) pour ne pas dupliquer la logique.
 */
object ProductImagePolicy {

    /** Plus grand côté conservé : net sur une grille de téléphone, léger à stocker (environ 100 à 250 Ko). */
    const val MAX_DIMENSION_PX: Int = 1024

    const val JPEG_QUALITY: Int = 85

    const val MAX_SOURCE_BYTES: Int = IssuerLogoPolicy.MAX_SOURCE_BYTES

    const val MAX_SOURCE_PIXELS: Long = IssuerLogoPolicy.MAX_SOURCE_PIXELS

    fun targetSize(width: Int, height: Int): Pair<Int, Int> = IssuerLogoPolicy.targetSize(width, height, MAX_DIMENSION_PX)

    fun sampleSize(width: Int, height: Int): Int = IssuerLogoPolicy.sampleSize(width, height, MAX_DIMENSION_PX)

    fun detectMimeType(header: ByteArray): String? = IssuerLogoPolicy.detectMimeType(header)

    /** Taille maximale d'une photo lue dans une sauvegarde (les photos écrites par l'appli en font bien moins). */
    const val MAX_BACKUP_IMAGE_BYTES: Int = 4 * 1024 * 1024

    /** Une photo de sauvegarde n'est restaurée que si c'est un JPEG non vide de taille raisonnable. */
    fun isRestorableImage(bytes: ByteArray): Boolean =
        bytes.isNotEmpty() && bytes.size <= MAX_BACKUP_IMAGE_BYTES && detectMimeType(bytes) == "image/jpeg"

    private val safeFileName = Regex("^[A-Za-z0-9-]{1,64}\\.jpg$")

    /** Nom simple généré par l'application : jamais de `/`, `..` ni d'extension inattendue. */
    fun isSafeFileName(name: String): Boolean = safeFileName.matches(name)
}
