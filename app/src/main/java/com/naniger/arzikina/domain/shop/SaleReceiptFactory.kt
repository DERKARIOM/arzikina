package com.naniger.arzikina.domain.shop

import com.naniger.arzikina.domain.model.IssuedReceipt
import com.naniger.arzikina.domain.model.IssuerProfile
import com.naniger.arzikina.domain.model.ReceiptDiscount
import com.naniger.arzikina.domain.model.ReceiptLine
import com.naniger.arzikina.domain.model.ReceiptPricing
import com.naniger.arzikina.domain.model.PaymentMethod

/**
 * Construit le reçu d'une vente validée pour le module « Reçus émis » (aperçu, PDF, partage, numérotation).
 *
 * Le reçu est un INSTANTANÉ : lignes (nom et prix appliqués), remise, client et coordonnées de
 * l'émetteur sont copiés. Modifier ou archiver un produit, ou changer le profil du commerce, ne
 * modifie donc jamais un reçu déjà émis.
 *
 * Le total du reçu est celui de la vente : mêmes lignes, même `ReceiptCalculator`, et la remise est
 * reprise en MONTANT appliqué (un pourcentage arrondi donnerait le même montant, il n'y a donc aucun
 * écart possible).
 */
object SaleReceiptFactory {

    /**
     * @param defaultCustomerName libellé du client de passage (le reçu exige un nom ; texte traduit fourni par l'appelant).
     * @param descriptionPrefix début du libellé de la prestation, complété par la référence de la vente.
     */
    fun build(
        sale: Sale,
        profile: IssuerProfile,
        defaultCustomerName: String,
        descriptionPrefix: String
    ): IssuedReceipt {
        val pricing = ReceiptPricing(
            lines = sale.lines.map {
                ReceiptLine(label = it.productName, quantityMilli = it.quantityMilli, unitPriceMinor = it.unitPriceMinor)
            },
            discount = if (sale.discountMinor > 0L) ReceiptDiscount.Fixed(sale.discountMinor) else ReceiptDiscount.None,
            taxBasisPoints = 0
        )
        return IssuedReceipt(
            issuedAt = sale.soldAt,
            customerName = sale.customerName?.takeIf { it.isNotBlank() } ?: defaultCustomerName,
            customerPhone = sale.customerPhone,
            description = "$descriptionPrefix ${sale.reference}".trim(),
            amountMinor = sale.totalMinor,
            currencyCode = sale.currencyCode,
            paymentMethod = sale.paymentMethod ?: PaymentMethod.CASH,
            transactionReference = sale.reference,
            issuedByName = profile.defaultIssuedByName,
            issuedByTitle = profile.defaultIssuedByTitle,
            pricing = pricing,
            issuer = profile.toSnapshot().copy(signatureFileName = profile.signatureFileName)
        )
    }
}
