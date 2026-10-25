package com.naniger.arzikina.domain.shop

/**
 * Nature d'un mouvement de stock. Un mouvement est IMMUABLE : le stock d'un produit est la somme de
 * ses mouvements (voir [StockRules.stockOf]), jamais une valeur éditée à la main.
 *
 * [direction] : sens autorisé de la quantité signée du mouvement ([Direction.IN] = positive,
 * [Direction.OUT] = négative, [Direction.ANY] = l'un ou l'autre). [requiresReason] : un motif
 * explicite est obligatoire (corrections, pertes, mouvements libres).
 *
 * Stocké par NOM (texte), voir [ProductUnit] pour la règle de compatibilité : jamais renommer ni
 * supprimer une entrée.
 */
enum class StockMovementType(val direction: Direction, val requiresReason: Boolean) {
    /** Quantité saisie à la création du produit. */
    INITIAL(Direction.IN, false),

    /** Livraison / réapprovisionnement. */
    RESTOCK(Direction.IN, false),

    /** Vente confirmée (lié à une vente). */
    SALE(Direction.OUT, false),

    /** Article client réintégré au stock vendable après contrôle. */
    CUSTOMER_RETURN(Direction.IN, false),

    /** Produit endommagé, périmé ou perdu. */
    DAMAGED_OR_LOST(Direction.OUT, true),

    /** Correction manuelle justifiée (inventaire…), dans un sens ou l'autre. */
    ADJUSTMENT(Direction.ANY, true),

    /** Rétablissement du stock d'une vente annulée (lié à la vente annulée). */
    SALE_CANCELLATION(Direction.IN, false),

    /** Autre mouvement documenté. */
    OTHER(Direction.ANY, true);

    enum class Direction { IN, OUT, ANY }

    companion object {
        fun fromStored(value: String): StockMovementType = entries.firstOrNull { it.name == value } ?: OTHER
    }
}
