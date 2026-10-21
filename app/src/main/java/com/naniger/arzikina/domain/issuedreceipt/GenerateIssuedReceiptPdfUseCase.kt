package com.naniger.arzikina.domain.issuedreceipt

import com.naniger.arzikina.domain.issuedreceipt.pdf.IssuedReceiptPdfWriter
import com.naniger.arzikina.domain.repository.IssuedReceiptRepository
import com.naniger.arzikina.domain.repository.IssuerProfileRepository
import javax.inject.Inject

/**
 * Génère (ou régénère) le PDF d'un reçu DÉJÀ enregistré. Ne crée jamais de reçu : générer plusieurs
 * fois le PDF d'un même reçu ne produit donc aucun doublon (même enregistrement, même fichier).
 *
 * Logo : celui figé dans le reçu d'abord ; s'il a été remplacé depuis, le logo ACTUEL du profil.
 */
class GenerateIssuedReceiptPdfUseCase @Inject constructor(
    private val receipts: IssuedReceiptRepository,
    private val profile: IssuerProfileRepository,
    private val writer: IssuedReceiptPdfWriter
) {

    /** @return le nom du fichier PDF. @throws IssuedReceiptException */
    suspend operator fun invoke(receiptId: Long, language: String): String {
        val receipt = receipts.getReceipt(receiptId) ?: throw IssuedReceiptException.NotFound(receiptId)
        val logos = listOfNotNull(receipt.issuer.logoFileName, profile.getProfile()?.logoFileName).distinct()
        val fileName = writer.write(receipt, logos, language)
        receipts.markPdfGenerated(receiptId, fileName)
        return fileName
    }
}
