package com.naniger.arzikina.domain.issuedreceipt

import com.naniger.arzikina.domain.issuedreceipt.pdf.IssuedReceiptPdfWriter
import javax.inject.Inject

/**
 * Fournit le PDF d'un reçu enregistré pour l'ouvrir ou le partager (historique).
 *
 * Le PDF est TOUJOURS régénéré : le nom de fichier est déterministe (un fichier par reçu, jamais de
 * doublon) et la génération est rapide, ce qui garantit un PDF à jour (profil, logo, langue) et
 * couvre le cas d'un fichier supprimé du stockage. Ne crée jamais de reçu.
 */
class PrepareIssuedReceiptPdfUseCase @Inject constructor(
    private val generatePdf: GenerateIssuedReceiptPdfUseCase,
    private val writer: IssuedReceiptPdfWriter
) {

    /** @throws IssuedReceiptException (NotFound, PdfFailed) */
    suspend operator fun invoke(receiptId: Long, language: String): IssuedReceiptPdfFile {
        val fileName = generatePdf(receiptId, language)
        val uri = writer.contentUriFor(fileName)
            ?: throw IssuedReceiptException.PdfFailed(IllegalStateException("PDF introuvable après génération."))
        return IssuedReceiptPdfFile(fileName, uri)
    }
}
