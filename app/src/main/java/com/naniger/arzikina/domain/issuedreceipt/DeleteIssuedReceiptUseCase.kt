package com.naniger.arzikina.domain.issuedreceipt

import com.naniger.arzikina.domain.issuedreceipt.pdf.IssuedReceiptPdfWriter
import com.naniger.arzikina.domain.repository.IssuedReceiptRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Supprime un reçu (suppression douce : son numéro n'est jamais réutilisé) puis son fichier PDF.
 * Le nettoyage du fichier est « au mieux » : un échec n'annule pas la suppression du reçu (le PDF
 * orphelin est purgé avec le compte, voir `AccountDataWiper`).
 */
class DeleteIssuedReceiptUseCase @Inject constructor(
    private val receipts: IssuedReceiptRepository,
    private val writer: IssuedReceiptPdfWriter
) {

    suspend operator fun invoke(receiptId: Long) {
        val pdfFileName = receipts.getReceipt(receiptId)?.pdfFileName
        receipts.deleteReceipt(receiptId)
        if (pdfFileName != null) {
            try {
                writer.delete(pdfFileName)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Fichier orphelin sans conséquence fonctionnelle.
            }
        }
    }
}
