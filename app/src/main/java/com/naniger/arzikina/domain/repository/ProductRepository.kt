package com.naniger.arzikina.domain.repository

import com.naniger.arzikina.domain.shop.Product
import com.naniger.arzikina.domain.shop.ProductCategory
import com.naniger.arzikina.domain.shop.ProductDraft
import com.naniger.arzikina.domain.shop.ProductFilter
import com.naniger.arzikina.domain.shop.ProductSummary
import com.naniger.arzikina.domain.shop.ShopStats
import kotlinx.coroutines.flow.Flow

/**
 * Produits et catégories de produits de la boutique active. Toutes les écritures sont atomiques
 * (une transaction de base) ; le stock n'est JAMAIS écrit directement par ce contrat — voir
 * [saveProduct] pour le seul cas (stock initial) et l'étape « Stock » pour les autres mouvements.
 */
interface ProductRepository {

    /** Liste filtrée et triée PAR LA BASE (recherche nom/référence/code-barres, catégorie, disponibilité). */
    fun observeProducts(filter: ProductFilter): Flow<List<ProductSummary>>

    /** Indicateurs de stock des produits actifs ; `null` tant que la boutique n'existe pas. */
    fun observeStats(): Flow<ShopStats?>

    /** Produits actifs en stock faible ou épuisés, les plus critiques d'abord, limités à [limit]. */
    fun observeStockAlerts(limit: Int): Flow<List<ProductSummary>>

    fun observeProduct(id: Long): Flow<Product?>

    suspend fun getProduct(id: Long): Product?

    fun observeCategories(): Flow<List<ProductCategory>>

    /**
     * Crée (id = 0) ou met à jour un produit et retourne son id. À la création, un stock initial non
     * nul produit un mouvement `INITIAL` dans la MÊME transaction ; à la mise à jour, le stock n'est pas touché.
     *
     * @throws com.naniger.arzikina.domain.shop.ProductException
     */
    suspend fun saveProduct(draft: ProductDraft): Long

    /** Archive (ou restaure) un produit : jamais supprimé, ses ventes passées le référencent. */
    suspend fun setArchived(id: Long, archived: Boolean)

    /** Crée la catégorie [name] (ou retourne celle qui porte déjà ce nom, sans tenir compte de la casse). */
    suspend fun createCategory(name: String): ProductCategory
}
