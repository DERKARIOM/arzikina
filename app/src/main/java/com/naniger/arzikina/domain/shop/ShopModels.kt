package com.naniger.arzikina.domain.shop

/**
 * Boutique de l'utilisateur. V1 : une seule boutique exposée par l'interface (voir `ShopRepository`),
 * mais toutes les données portent déjà son identifiant.
 *
 * @param accountId compte `BOUTIQUE` crédité par défaut lors d'une vente ; `null` tant qu'aucun n'est choisi.
 */
data class Shop(
    val id: Long,
    val name: String,
    val currencyCode: String,
    val accountId: Long?
)

/** Catégorie de produits d'une boutique (distincte des catégories de transactions). */
data class ProductCategory(val id: Long, val name: String)

/**
 * Produit complet (fiche détaillée). Les montants sont en unités mineures, les quantités en
 * milli-unités (voir [StockRules.QUANTITY_UNIT_MILLI]).
 *
 * @param stockMilli quantité disponible, somme des mouvements de stock : jamais modifiée directement.
 * @param purchasePriceMinor prix d'achat unitaire ; `null` s'il n'est pas renseigné (jamais inventé).
 * @param expiryDate date de péremption (epoch millis), `null` si sans objet.
 */
data class Product(
    val id: Long,
    val shopId: Long,
    val categoryId: Long?,
    val name: String,
    val description: String?,
    val sku: String?,
    val barcode: String?,
    val unit: ProductUnit,
    val purchasePriceMinor: Long?,
    val salePriceMinor: Long,
    val minStockMilli: Long,
    val stockMilli: Long,
    val expiryDate: Long?,
    val isArchived: Boolean,
    val createdAt: Long,
    /** Photo principale (fichier privé de l'appli) ; `null` si le produit n'en a pas ou si le fichier a disparu. */
    val imageFile: java.io.File? = null
) {
    val stockLevel: StockLevel get() = StockRules.levelOf(stockMilli, minStockMilli)

    /** Marge unitaire (vente − achat), `null` si le prix d'achat est inconnu. */
    val unitMarginMinor: Long? get() = purchasePriceMinor?.let { salePriceMinor - it }
}

/**
 * Saisie d'un produit (création ou modification).
 *
 * [initialStockMilli] n'est pris en compte qu'à la CRÉATION (il devient le mouvement `INITIAL`) : en
 * modification, le stock ne change jamais par ce chemin — seulement par un mouvement tracé.
 */
data class ProductDraft(
    val id: Long = 0L,
    val name: String,
    val description: String? = null,
    val sku: String? = null,
    val barcode: String? = null,
    val categoryId: Long? = null,
    val unit: ProductUnit = ProductUnit.PIECE,
    val purchasePriceMinor: Long? = null,
    val salePriceMinor: Long,
    val minStockMilli: Long = 0L,
    val initialStockMilli: Long = 0L,
    val expiryDate: Long? = null,
    /** Évolution de la photo à l'enregistrement (conserver par défaut). */
    val imageChange: ProductImageChange = ProductImageChange.Keep
)

/** Ligne de la liste des produits : uniquement ce qu'elle affiche (la fiche complète est chargée à part). */
data class ProductSummary(
    val id: Long,
    val name: String,
    val sku: String?,
    val barcode: String?,
    val unit: ProductUnit,
    val salePriceMinor: Long,
    val stockMilli: Long,
    val minStockMilli: Long,
    val categoryId: Long?,
    val isArchived: Boolean,
    /** Photo principale pour les vignettes ; `null` sans photo (la présentation affiche alors un pictogramme). */
    val imageFile: java.io.File? = null
) {
    val stockLevel: StockLevel get() = StockRules.levelOf(stockMilli, minStockMilli)
}

/** Filtre de disponibilité de la liste (voir `ProductDao.observeSummaries`, qui en reprend les codes). */
enum class ProductAvailability(val code: Int) {
    ALL(0),
    IN_STOCK(1),

    /** Stock faible : encore disponible mais au seuil minimal ou en dessous. */
    LOW(2),
    OUT_OF_STOCK(3);

    companion object {
        fun fromCode(code: Int): ProductAvailability = entries.firstOrNull { it.code == code } ?: ALL
    }
}

/** Tri de la liste (codes repris par la requête SQL). */
enum class ProductSort(val code: Int) {
    NAME(0),
    PRICE(1),
    QUANTITY(2),
    NEWEST(3);

    companion object {
        fun fromCode(code: Int): ProductSort = entries.firstOrNull { it.code == code } ?: NAME
    }
}

/** Critères de la liste des produits. [archived] : `false` = produits actifs, `true` = produits archivés. */
data class ProductFilter(
    val query: String = "",
    val categoryId: Long? = null,
    val availability: ProductAvailability = ProductAvailability.ALL,
    val sort: ProductSort = ProductSort.NAME,
    val archived: Boolean = false
) {
    /** `true` si au moins un critère diffère de l'affichage par défaut (hors tri). */
    val isActive: Boolean
        get() = query.isNotBlank() || categoryId != null || availability != ProductAvailability.ALL || archived
}

/**
 * Indicateurs de stock du tableau de bord, calculés sur les produits ACTIFS.
 *
 * @param stockValueAtCostMinor valeur du stock au prix d'achat, comptée sur les seuls produits dont
 * le prix d'achat est renseigné ; `null` si aucun ne l'est (état vide explicite, jamais un zéro trompeur).
 * @param productsWithoutCost nombre de produits en stock dont le prix d'achat est inconnu (la valeur
 * d'achat est donc partielle quand il est > 0).
 */
data class ShopStats(
    val totalProducts: Int,
    val inStock: Int,
    val lowStock: Int,
    val outOfStock: Int,
    val stockValueAtCostMinor: Long?,
    val productsWithoutCost: Int,
    val stockValueAtSaleMinor: Long
)
