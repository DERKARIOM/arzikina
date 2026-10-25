package com.naniger.arzikina.domain.shop

import com.naniger.arzikina.domain.issuedreceipt.ReceiptCalculator
import com.naniger.arzikina.domain.issuedreceipt.ReceiptTotals
import com.naniger.arzikina.domain.model.ReceiptDiscount
import com.naniger.arzikina.domain.model.ReceiptLine
import com.naniger.arzikina.domain.model.ReceiptPricing

/** Ligne de panier : prix unitaire et quantité (milli-unités) APPLIQUÉS au moment de la vente. */
data class SaleLineDraft(
    val productId: Long,
    val unitPriceMinor: Long,
    val quantityMilli: Long
)

/**
 * Calcul des totaux d'une vente. Délègue à [ReceiptCalculator] (module « Gestion de reçus ») plutôt
 * que de dupliquer les règles d'arrondi : le total de la vente et le total du reçu généré à partir
 * d'elle sont ainsi identiques PAR CONSTRUCTION.
 *
 * Règles (héritées de [ReceiptCalculator]) : montant de ligne = prix × quantité / 1000 arrondi à
 * l'unité mineure (demi vers le haut) ; remise en points de base ([ReceiptDiscount.Percent]) ou
 * montant fixe plafonné au sous-total ; aucun `Double`. Pas de taxe en V1.
 */
object SaleCalculator {

    /** Totaux, ou `null` si le panier est vide, une ligne invalide ou un montant hors plafond. */
    fun totalsOrNull(lines: List<SaleLineDraft>, discount: ReceiptDiscount = ReceiptDiscount.None): ReceiptTotals? {
        if (lines.isEmpty()) return null
        val pricing = ReceiptPricing(
            lines = lines.map { ReceiptLine(label = "", quantityMilli = it.quantityMilli, unitPriceMinor = it.unitPriceMinor) },
            discount = discount,
            taxBasisPoints = 0
        )
        return ReceiptCalculator.totalsOrNull(pricing)
    }

    /** Montant d'une ligne, ou `null` si invalide ou hors plafond. */
    fun lineTotalOrNull(line: SaleLineDraft): Long? =
        ReceiptCalculator.lineTotalOrNull(
            ReceiptLine(label = "", quantityMilli = line.quantityMilli, unitPriceMinor = line.unitPriceMinor)
        )

    /**
     * Identifiants des produits dont la quantité TOTALE demandée dans le panier dépasse le stock
     * connu ([stockByProduct]). Les quantités d'un même produit présent sur plusieurs lignes sont
     * cumulées avant comparaison ; un produit absent de [stockByProduct] compte pour un stock nul.
     * Liste vide = vente possible.
     */
    fun insufficientProducts(lines: List<SaleLineDraft>, stockByProduct: Map<Long, Long>): List<Long> =
        lines.groupBy { it.productId }
            .filter { (productId, productLines) ->
                val requested = productLines.fold(0L) { acc, l -> Math.addExact(acc, l.quantityMilli) }
                !StockRules.canSell(stockByProduct[productId] ?: 0L, requested)
            }
            .keys
            .toList()
}
