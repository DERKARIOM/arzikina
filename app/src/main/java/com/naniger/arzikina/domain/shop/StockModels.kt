package com.naniger.arzikina.domain.shop

/**
 * Mouvement de stock (immuable).
 *
 * @param deltaMilli quantité SIGNÉE en milli-unités (positive = entrée, négative = sortie).
 * @param saleId vente d'origine pour les mouvements `SALE` / `SALE_CANCELLATION`, `null` sinon.
 */
data class StockMovement(
    val id: Long,
    val productId: Long,
    val type: StockMovementType,
    val deltaMilli: Long,
    val unitCostMinor: Long?,
    val reason: String?,
    val reference: String?,
    val saleId: Long?,
    val occurredAt: Long
)

/**
 * Demande d'un mouvement manuel (voir [StockMovementPolicy.MANUAL_TYPES]).
 *
 * @param updatePurchasePrice réapprovisionnement seulement : si vrai et [unitCostMinor] renseigné, le prix
 * d'achat du produit prend cette valeur (dernier coût connu) dans la même transaction.
 */
data class StockMovementRequest(
    val productId: Long,
    val type: StockMovementType,
    val deltaMilli: Long,
    val reason: String? = null,
    val reference: String? = null,
    val unitCostMinor: Long? = null,
    val updatePurchasePrice: Boolean = false
)

/** Échecs métier des mouvements de stock. */
sealed class StockException(message: String) : Exception(message) {
    class Rejected(val error: StockChangeError) : StockException("Mouvement refusé : $error")
    class ProductNotFound(val productId: Long) : StockException("Produit introuvable : $productId")
    class NoSession : StockException("Aucune session")
}
