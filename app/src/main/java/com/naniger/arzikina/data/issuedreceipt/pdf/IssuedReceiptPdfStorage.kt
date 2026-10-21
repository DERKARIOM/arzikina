package com.naniger.arzikina.data.issuedreceipt.pdf

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stockage privé des PDF de reçus : `files/issued_receipts/<userId>/<numéro>.pdf`. Un dossier PAR
 * utilisateur : deux comptes sur le même appareil peuvent avoir le même numéro sans se
 * remplacer mutuellement. L'écriture est atomique (fichier temporaire puis renommage) : une
 * génération qui échoue ne détruit jamais le PDF précédent.
 */
@Singleton
class IssuedReceiptPdfStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private fun directory(userId: Long): File = File(File(context.filesDir, ROOT), userId.toString())

    /** Fichier du PDF, ou `null` si le nom est dangereux ou le fichier absent. */
    fun resolveFile(userId: Long, fileName: String): File? {
        if (!isSafeName(fileName)) return null
        return File(directory(userId), fileName).takeIf { it.isFile }
    }

    /** @throws IOException si l'écriture ou le renommage échoue (le PDF précédent reste intact). */
    fun writeAtomically(userId: Long, fileName: String, writer: (OutputStream) -> Unit) {
        require(isSafeName(fileName)) { "Nom de PDF non sûr." }
        val dir = directory(userId).apply { if (!exists() && !mkdirs()) throw IOException("Dossier PDF inaccessible.") }
        val target = File(dir, fileName)
        val temp = File(dir, "$fileName.tmp")
        try {
            temp.outputStream().use { writer(it) }
            if (!temp.renameTo(target)) {
                if (target.exists() && !target.delete()) throw IOException("Remplacement du PDF impossible.")
                if (!temp.renameTo(target)) throw IOException("Renommage du PDF impossible.")
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    fun delete(userId: Long, fileName: String): Boolean =
        isSafeName(fileName) && File(directory(userId), fileName).let { it.exists() && it.delete() }

    /** Supprime tous les PDF de l'utilisateur (suppression de compte). */
    fun deleteAllForUser(userId: Long) {
        directory(userId).deleteRecursively()
    }

    private fun isSafeName(name: String): Boolean = SAFE_NAME.matches(name)

    private companion object {
        const val ROOT = "issued_receipts"
        val SAFE_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._\\-]{0,40}\\.pdf$")
    }
}
