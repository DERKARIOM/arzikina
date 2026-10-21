package com.naniger.arzikina.domain.issuedreceipt.pdf

import com.naniger.arzikina.domain.model.IssuedReceipt

/**
 * Écrit le PDF d'un reçu (implémentation Android dans la couche données). Le fichier porte un nom
 * DÉTERMINISTE (`<numéro>.pdf`) : régénérer le PDF d'un même reçu remplace le fichier au lieu d'en
 * accumuler.
 */
interface IssuedReceiptPdfWriter {

    /**
     * @param logoCandidates noms de fichiers de logo à essayer dans l'ordre ; le premier réellement
     * présent est utilisé, aucun = reçu sans logo.
     * @return le nom du fichier PDF écrit (voir [com.naniger.arzikina.domain.model.IssuedReceipt.pdfFileName]).
     * @throws com.naniger.arzikina.domain.issuedreceipt.IssuedReceiptException.PdfFailed
     */
    suspend fun write(receipt: IssuedReceipt, logoCandidates: List<String>, language: String): String

    /**
     * URI `content://` (FileProvider) du PDF [fileName] de l'utilisateur courant, pour l'ouvrir ou le
     * partager ; `null` si le fichier n'existe plus (il peut être régénéré à tout moment).
     */
    suspend fun contentUriFor(fileName: String): String?

    /** Supprime le fichier PDF [fileName] de l'utilisateur courant (sans effet s'il n'existe pas). */
    suspend fun delete(fileName: String)
}
