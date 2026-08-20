package com.arzikina.ne.data.receipts

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.arzikina.ne.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stockage physique des reçus PDF dans le répertoire privé de l'application — cahier des charges
 * "Gestion des reçus", sections 2/17/18 : `files/receipts/`, jamais le stockage public, jamais de
 * permission de stockage demandée (inutile : `context.filesDir` est TOUJOURS accessible sans
 * permission, quelle que soit la version d'Android).
 *
 * Ne connaît JAMAIS [com.arzikina.ne.domain.model.Receipt] ni Room (voir [ReceiptRepositoryImpl],
 * seul appelant prévu) : uniquement responsable des octets sur le disque, séparation stricte des
 * responsabilités (même principe que `data/security/CardCipher`, un utilitaire d'infrastructure
 * pur).
 *
 * [Receipt.localPath] stocke le chemin RETOURNÉ par [copyToPrivateStorage] (relatif à
 * [Context.getFilesDir], voir sa doc) — jamais un chemin absolu, voir le raisonnement complet dans
 * la doc de [com.arzikina.ne.domain.model.Receipt.localPath].
 */
@Singleton
class ReceiptFileStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Créé à la demande (jamais au démarrage de l'app) : voir cahier des charges section 17,
     * "créer le dossier automatiquement si nécessaire". */
    private val receiptsDirectory: File
        get() = File(context.filesDir, RECEIPTS_DIRECTORY_NAME).apply { if (!exists()) mkdirs() }

    /**
     * Copie intégralement le contenu de [sourceUri] (URI potentiellement TEMPORAIRE, ex. `content://`
     * fourni par l'app source d'un partage ou par le sélecteur de fichiers Android) vers un nouveau
     * fichier local — jamais un simple `takePersistableUriPermission` sur l'URI d'origine, qui
     * resterait dépendant de l'app/du fichier source (voir cahier des charges section 2 : "ne jamais
     * dépendre uniquement de l'URI temporaire fournie par l'application source").
     *
     * Nom physique généré (UUID, extension `.pdf` fixe) — jamais dérivé du nom d'origine du fichier :
     * élimine toute collision ET tout caractère invalide pour le système de fichiers (cahier des
     * charges section 17), le nom AFFICHÉ à l'utilisateur reste entièrement séparé (voir
     * [com.arzikina.ne.domain.model.Receipt.fileName]).
     *
     * @throws IllegalStateException si [sourceUri] n'a pas pu être lu (ex. déjà révoqué par l'app
     * source au moment de l'appel).
     */
    fun copyToPrivateStorage(sourceUri: Uri): CopiedReceiptFile {
        val physicalFileName = "${UUID.randomUUID()}.pdf"
        val destinationFile = File(receiptsDirectory, physicalFileName)

        val bytesCopied = context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destinationFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Impossible de lire le fichier source (URI invalide ou révoquée).")

        return CopiedReceiptFile(
            relativePath = "$RECEIPTS_DIRECTORY_NAME/$physicalFileName",
            fileSize = bytesCopied
        )
    }

    /**
     * Écrit [bytes] tel quel dans un nouveau fichier local — utilisé uniquement par la restauration
     * d'une sauvegarde (voir `BackupRepositoryImpl.importBackup`), où le contenu du PDF est déjà en
     * mémoire (décodé depuis Base64, voir `ReceiptDto.pdfBase64`) plutôt que derrière une [Uri] à
     * lire comme dans [copyToPrivateStorage]. Même génération de nom physique (UUID) et même type de
     * retour que [copyToPrivateStorage], pour les mêmes raisons (voir sa doc) — [fileSize] retourné
     * ici est TOUJOURS `bytes.size`, jamais une valeur annoncée par ailleurs (même principe que
     * [copyToPrivateStorage], qui retourne le nombre d'octets réellement copiés).
     */
    fun writeBytes(bytes: ByteArray): CopiedReceiptFile {
        val physicalFileName = "${UUID.randomUUID()}.pdf"
        val destinationFile = File(receiptsDirectory, physicalFileName)
        destinationFile.writeBytes(bytes)

        return CopiedReceiptFile(
            relativePath = "$RECEIPTS_DIRECTORY_NAME/$physicalFileName",
            fileSize = bytes.size.toLong()
        )
    }

    /**
     * Nom d'affichage d'origine de [sourceUri] (colonne [OpenableColumns.DISPLAY_NAME]) — `null` si
     * le fournisseur de contenu source ne l'expose pas (rare mais possible, voir cahier des charges
     * "Gestion des reçus" section 4 : l'appelant doit alors se rabattre sur un nom générique). Jamais
     * d'exception propagée : un simple échec de lecture ne doit jamais faire échouer tout l'import.
     */
    fun queryDisplayName(sourceUri: Uri): String? = runCatching {
        context.contentResolver.query(sourceUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val nameColumnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameColumnIndex == -1 || !cursor.moveToFirst()) null else cursor.getString(nameColumnIndex)
            }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Résout un [com.arzikina.ne.domain.model.Receipt.localPath] (chemin relatif) vers le [File]
     * réel sur le disque — jamais l'inverse (aucune fonction ne reconstruit un chemin relatif à
     * partir d'un [File], le chemin relatif est TOUJOURS celui retourné par [copyToPrivateStorage]
     * à la création). */
    fun resolveFile(relativePath: String): File = File(context.filesDir, relativePath)

    /** `true` si un fichier existait réellement et a été supprimé — `false` sinon (jamais
     * d'exception : un fichier déjà absent ne doit jamais faire échouer une suppression de reçu,
     * voir `ReceiptRepositoryImpl.deleteReceipt`). */
    fun deleteFile(relativePath: String): Boolean {
        val file = resolveFile(relativePath)
        return file.exists() && file.delete()
    }

    /**
     * URI `content://` sécurisée via [FileProvider] (voir `AndroidManifest.xml`,
     * `res/xml/file_paths.xml`) — seule façon autorisée de partager/ouvrir un reçu depuis une autre
     * application (cahier des charges section 9 : "ne jamais exposer directement un chemin de
     * fichier privé de l'application").
     */
    fun contentUriFor(relativePath: String): Uri =
        FileProvider.getUriForFile(
            context,
            context.packageName + Constants.FILE_PROVIDER_AUTHORITY_SUFFIX,
            resolveFile(relativePath)
        )

    /** @param relativePath voir [com.arzikina.ne.domain.model.Receipt.localPath].
     * @param fileSize taille RÉELLEMENT copiée (jamais une valeur annoncée par la source, potentiellement
     * fausse ou absente — voir cahier des charges section 3). */
    data class CopiedReceiptFile(val relativePath: String, val fileSize: Long)

    private companion object {
        const val RECEIPTS_DIRECTORY_NAME = "receipts"
    }
}
