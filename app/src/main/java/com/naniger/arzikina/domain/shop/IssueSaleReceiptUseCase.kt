package com.naniger.arzikina.domain.shop

import com.naniger.arzikina.domain.issuedreceipt.IssuedReceiptException
import com.naniger.arzikina.domain.repository.IssuedReceiptRepository
import com.naniger.arzikina.domain.repository.IssuerProfileRepository
import com.naniger.arzikina.domain.repository.SaleRepository
import javax.inject.Inject

/**
 * Émet (une seule fois) le reçu d'une vente validée et le rattache à la vente.
 *
 * - Jamais de reçu pour une vente non validée ou annulée sans reçu.
 * - Idempotent : si la vente a déjà un reçu existant, il est renvoyé tel quel.
 * - Concurrence : le rattachement est conditionnel (`issuedReceiptId` encore vide ou périmé) ; si un
 *   autre appel a gagné, le reçu créé en trop est supprimé et celui du gagnant est renvoyé.
 */
class IssueSaleReceiptUseCase @Inject constructor(
    private val sales: SaleRepository,
    private val receipts: IssuedReceiptRepository,
    private val profiles: IssuerProfileRepository
) {

    /** @return l'identifiant du reçu de la vente. */
    suspend operator fun invoke(saleId: Long, defaultCustomerName: String, descriptionPrefix: String): Long {
        val sale = sales.getSale(saleId) ?: throw SaleException.NotFound(saleId)
        val staleId = sale.issuedReceiptId
        if (staleId != null && receipts.getReceipt(staleId) != null) return staleId
        if (sale.status != SaleStatus.CONFIRMED) throw SaleException.NotConfirmed(saleId)

        val profile = profiles.getProfile() ?: throw SaleException.IssuerProfileIncomplete()
        val receipt = SaleReceiptFactory.build(sale, profile, defaultCustomerName, descriptionPrefix)
        val receiptId = try {
            receipts.saveReceipt(receipt)
        } catch (e: IssuedReceiptException.Invalid) {
            throw SaleException.IssuerProfileIncomplete()
        }

        if (sales.linkReceipt(saleId, receiptId, staleId)) return receiptId
        // Un autre appel a rattaché son reçu entre-temps : on retire le nôtre.
        receipts.deleteReceipt(receiptId)
        return sales.getSale(saleId)?.issuedReceiptId ?: throw SaleException.NotFound(saleId)
    }
}
