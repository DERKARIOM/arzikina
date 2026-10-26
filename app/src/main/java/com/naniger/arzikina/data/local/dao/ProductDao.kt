package com.naniger.arzikina.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Update
import com.naniger.arzikina.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

/** Colonnes strictement nécessaires à une ligne de liste : Room ne lit que celles-ci (pas de description ni de dates). */
data class ProductSummaryRow(
    val id: Long,
    val name: String,
    val sku: String?,
    val barcode: String?,
    val unit: String,
    val salePriceMinor: Long,
    val stockMilli: Long,
    val minStockMilli: Long,
    val categoryId: Long?,
    val isArchived: Boolean,
    val imageFileName: String?
)

/** Agrégats du tableau de bord (un seul balayage de la table, sur les produits actifs). */
data class ShopStatsRow(
    val totalProducts: Int,
    val inStock: Int,
    val lowStock: Int,
    val outOfStock: Int,
    val productsWithCost: Int,
    val productsWithoutCost: Int,
    val stockValueAtCostMinor: Long,
    val stockValueAtSaleMinor: Long
)

/**
 * Accès à la table `products`. Filtrage systématique par `userId` ET `shopId` (jamais de mélange entre
 * boutiques), suppression douce `deletedAt IS NULL`. Recherche, filtres et tri sont faits EN SQL : une
 * boutique peut compter des milliers de produits, rien n'est filtré en mémoire.
 */
@Dao
interface ProductDao {

    /**
     * [pattern] : motif `LIKE` déjà échappé (voir `SqlLike.containsPattern`). [categoryId] `null` = toutes.
     * [availability] et [sort] : codes de `ProductAvailability` (0 tout, 1 en stock, 2 faible, 3 épuisé) et
     * `ProductSort` (0 nom, 1 prix, 2 quantité, 3 plus récent). Les CASE du tri laissent NULL les autres
     * clés, ignorées : un seul critère est actif à la fois ; `id` départage les ex æquo.
     */
    @Query(
        "SELECT id, name, sku, barcode, unit, salePriceMinor, stockMilli, minStockMilli, categoryId, isArchived, imageFileName " +
            "FROM products WHERE userId = :userId AND shopId = :shopId AND deletedAt IS NULL " +
            "AND isArchived = :archived " +
            "AND (:categoryId IS NULL OR categoryId = :categoryId) " +
            "AND (:availability = 0 " +
            "OR (:availability = 1 AND stockMilli > 0) " +
            "OR (:availability = 2 AND stockMilli > 0 AND minStockMilli > 0 AND stockMilli <= minStockMilli) " +
            "OR (:availability = 3 AND stockMilli <= 0)) " +
            "AND (name LIKE :pattern ESCAPE '\\' OR sku LIKE :pattern ESCAPE '\\' OR barcode LIKE :pattern ESCAPE '\\') " +
            "ORDER BY CASE WHEN :sort = 0 THEN name END COLLATE NOCASE ASC, " +
            "CASE WHEN :sort = 1 THEN salePriceMinor END ASC, " +
            "CASE WHEN :sort = 2 THEN stockMilli END ASC, " +
            "CASE WHEN :sort = 3 THEN createdAt END DESC, id DESC"
    )
    fun observeSummaries(
        userId: Long,
        shopId: Long,
        pattern: String,
        categoryId: Long?,
        availability: Int,
        sort: Int,
        archived: Boolean
    ): Flow<List<ProductSummaryRow>>

    /** Produits actifs épuisés ou au seuil minimal : épuisés d'abord, puis par stock croissant. */
    @Query(
        "SELECT id, name, sku, barcode, unit, salePriceMinor, stockMilli, minStockMilli, categoryId, isArchived, imageFileName " +
            "FROM products WHERE userId = :userId AND shopId = :shopId AND deletedAt IS NULL AND isArchived = 0 " +
            "AND (stockMilli <= 0 OR (minStockMilli > 0 AND stockMilli <= minStockMilli)) " +
            "ORDER BY CASE WHEN stockMilli <= 0 THEN 0 ELSE 1 END, stockMilli ASC, name COLLATE NOCASE, id " +
            "LIMIT :limit"
    )
    fun observeStockAlerts(userId: Long, shopId: Long, limit: Int): Flow<List<ProductSummaryRow>>

    /**
     * Valeurs calculées en flottant puis arrondies : un produit × une quantité démesurés ne peuvent pas
     * faire échouer la somme (SQLite lève une erreur de débordement sur `SUM` d'entiers, pas de flottants).
     */
    @Query(
        "SELECT COUNT(*) AS totalProducts, " +
            "COALESCE(SUM(CASE WHEN stockMilli > 0 THEN 1 ELSE 0 END), 0) AS inStock, " +
            "COALESCE(SUM(CASE WHEN stockMilli > 0 AND minStockMilli > 0 AND stockMilli <= minStockMilli THEN 1 ELSE 0 END), 0) AS lowStock, " +
            "COALESCE(SUM(CASE WHEN stockMilli <= 0 THEN 1 ELSE 0 END), 0) AS outOfStock, " +
            "COALESCE(SUM(CASE WHEN stockMilli > 0 AND purchasePriceMinor IS NOT NULL THEN 1 ELSE 0 END), 0) AS productsWithCost, " +
            "COALESCE(SUM(CASE WHEN stockMilli > 0 AND purchasePriceMinor IS NULL THEN 1 ELSE 0 END), 0) AS productsWithoutCost, " +
            "CAST(ROUND(COALESCE(SUM(CASE WHEN stockMilli > 0 AND purchasePriceMinor IS NOT NULL " +
            "THEN stockMilli * 1.0 * purchasePriceMinor / 1000 END), 0)) AS INTEGER) AS stockValueAtCostMinor, " +
            "CAST(ROUND(COALESCE(SUM(CASE WHEN stockMilli > 0 " +
            "THEN stockMilli * 1.0 * salePriceMinor / 1000 END), 0)) AS INTEGER) AS stockValueAtSaleMinor " +
            "FROM products WHERE userId = :userId AND shopId = :shopId AND deletedAt IS NULL AND isArchived = 0"
    )
    fun observeStats(userId: Long, shopId: Long): Flow<ShopStatsRow>

    @Query("SELECT * FROM products WHERE id = :id AND userId = :userId AND deletedAt IS NULL")
    fun observeById(id: Long, userId: Long): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE id = :id AND userId = :userId AND deletedAt IS NULL")
    suspend fun getById(id: Long, userId: Long): ProductEntity?

    /** Autre produit (archivé ou non) portant ce code-barres, hors [excludeId] (0 à la création). */
    @Query(
        "SELECT id FROM products WHERE userId = :userId AND shopId = :shopId AND deletedAt IS NULL " +
            "AND barcode = :barcode AND id <> :excludeId LIMIT 1"
    )
    suspend fun findIdByBarcode(userId: Long, shopId: Long, barcode: String, excludeId: Long): Long?

    /** Autre produit (archivé ou non) portant cette référence, hors [excludeId] (0 à la création). */
    @Query(
        "SELECT id FROM products WHERE userId = :userId AND shopId = :shopId AND deletedAt IS NULL " +
            "AND sku = :sku AND id <> :excludeId LIMIT 1"
    )
    suspend fun findIdBySku(userId: Long, shopId: Long, sku: String, excludeId: Long): Long?

    /** Produits d'une boutique par identifiants (une seule requête pour tout un panier). */
    @Query(
        "SELECT * FROM products WHERE userId = :userId AND shopId = :shopId AND deletedAt IS NULL " +
            "AND id IN (:ids)"
    )
    suspend fun getByIds(userId: Long, shopId: Long, ids: List<Long>): List<ProductEntity>

    /** Retourne l'id de la ligne insérée. */
    @Insert
    suspend fun insert(product: ProductEntity): Long

    @Update
    suspend fun update(product: ProductEntity)

    /**
     * Met à jour UNIQUEMENT le cache de stock (jamais l'entité entière : une édition de fiche concurrente
     * ne peut pas écraser le stock). À appeler dans la transaction qui écrit le mouvement correspondant.
     */
    @Query("UPDATE products SET stockMilli = :stockMilli, updatedAt = :now WHERE id = :id AND userId = :userId")
    suspend fun updateStock(id: Long, userId: Long, stockMilli: Long, now: Long)

    @Query("UPDATE products SET purchasePriceMinor = :priceMinor, updatedAt = :now WHERE id = :id AND userId = :userId")
    suspend fun updatePurchasePrice(id: Long, userId: Long, priceMinor: Long, now: Long)

    /** Noms des photos référencées par [userId] (nettoyage des fichiers à la suppression du compte). */
    @Query("SELECT imageFileName FROM products WHERE userId = :userId AND imageFileName IS NOT NULL")
    suspend fun imageFileNamesForUser(userId: Long): List<String>

    /** Purge TOUTES les lignes de [userId] (restauration de sauvegarde, suppression du compte). */
    /** Réservé au gestionnaire de synchronisation (voir `ShopSyncHandler`) : `syncId` est unique tous utilisateurs confondus. */
    @Query("SELECT * FROM products WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): ProductEntity?

    /** Insère (id 0) ou met à jour la ligne telle que reçue du serveur. */
    @Upsert
    suspend fun upsert(row: ProductEntity): Long

    /** Rattrapage de la synchronisation : lignes jamais enfilées (voir `ShopSyncHandler.enqueueUnsynced`). */
    @Query("SELECT * FROM products WHERE userId = :userId AND deletedAt IS NULL AND syncId IS NULL ORDER BY id")
    suspend fun getUnsyncedForUser(userId: Long): List<ProductEntity>

    /** Attribue l'identité de synchronisation (voir `ShopSyncEnqueuer`) ; sans effet sur `updatedAt`. */
    @Query("UPDATE products SET syncId = :syncId WHERE id = :id AND userId = :userId")
    suspend fun setSyncId(id: Long, userId: Long, syncId: String)

    @Query("DELETE FROM products WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
