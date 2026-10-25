package com.naniger.arzikina.domain.shop

/**
 * Règles PURES de l'annulation d'une vente.
 *
 * Une annulation ne supprime rien : le stock vendu est remis par des mouvements `SALE_CANCELLATION`,
 * l'encaissement est neutralisé par une transaction inverse, la vente passe au statut `CANCELLED`
 * avec sa date et son motif.
 */
object SaleCancellation {

    const val MAX_REASON_LENGTH = 200

    /**
     * Nouveau stock après remise en stock de [quantityMilli], ou `null` si le résultat dépasserait le
     * plafond (jamais de débordement silencieux).
     */
    fun restoredStock(currentStockMilli: Long, quantityMilli: Long): Long? {
        if (quantityMilli <= 0L) return null
        val restored = try {
            Math.addExact(currentStockMilli, quantityMilli)
        } catch (e: ArithmeticException) {
            return null
        }
        return restored.takeIf { it <= StockRules.MAX_QUANTITY_MILLI }
    }

    fun cleanReason(input: String?): String? =
        input?.trim()?.replace(Regex("\\s+"), " ")?.take(MAX_REASON_LENGTH)?.takeIf { it.isNotEmpty() }
}
