package com.naniger.arzikina.data.issuedreceipt

import android.content.Context
import com.naniger.arzikina.domain.issuedreceipt.IssuerLogoPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stockage physique des logos d'émetteur dans le répertoire PRIVÉ de l'application
 * (`files/issuer_logos/`) : aucune permission, jamais lisible par une autre application. Ne connaît
 * ni Room ni le domaine métier : uniquement des octets sur disque. Les noms sont générés (UUID) et
 * validés par [IssuerLogoPolicy.isSafeFileName] avant tout accès (anti-traversée de répertoire).
 */
@Singleton
class IssuerLogoStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val directory: File
        get() = File(context.filesDir, DIRECTORY_NAME).apply { if (!exists()) mkdirs() }

    /** Écrit un NOUVEAU fichier et retourne son nom simple (stocké en base). Ne supprime rien. */
    fun write(bytes: ByteArray, extension: String): String {
        require(extension == "png" || extension == "jpg") { "Extension de logo non prise en charge." }
        val fileName = "${UUID.randomUUID()}.$extension"
        File(directory, fileName).writeBytes(bytes)
        return fileName
    }

    /**
     * Écrit un logo reçu par synchronisation SOUS SON NOM (celui référencé par le profil/les reçus).
     * Ne remplace jamais un fichier existant (nom = UUID, donc même contenu) ; écriture atomique
     * (fichier temporaire puis renommage) pour qu'un téléchargement interrompu ne laisse jamais un
     * logo tronqué. @return `false` si le nom est dangereux ; `true` si le fichier est présent après l'appel.
     */
    fun writeNamed(fileName: String, bytes: ByteArray): Boolean {
        if (!IssuerLogoPolicy.isSafeFileName(fileName)) return false
        val target = File(directory, fileName)
        if (target.isFile) return true
        val temp = File(directory, "$fileName.part")
        return try {
            temp.writeBytes(bytes)
            temp.renameTo(target) || target.isFile
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    /** Fichier du logo, ou `null` si le nom est dangereux ou si le fichier n'existe pas. */
    fun resolveFile(fileName: String): File? {
        if (!IssuerLogoPolicy.isSafeFileName(fileName)) return null
        return File(directory, fileName).takeIf { it.isFile }
    }

    /** Supprime le fichier s'il existe ; ne lève jamais (un fichier déjà absent n'est pas une erreur). */
    fun delete(fileName: String): Boolean {
        if (!IssuerLogoPolicy.isSafeFileName(fileName)) return false
        return File(directory, fileName).let { it.exists() && it.delete() }
    }

    private companion object {
        const val DIRECTORY_NAME = "issuer_logos"
    }
}
