package com.naniger.arzikina.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.naniger.arzikina.data.local.entity.SaleLineEntity
import kotlinx.coroutines.flow.Flow

/** Accès à la table `sale_lines`. Filtrage systématique par `userId` (voir `AccountDao`). */
@Dao
interface SaleLineDao {

    @Insert
    suspend fun insertAll(lines: List<SaleLineEntity>)

    @Query("SELECT * FROM sale_lines WHERE userId = :userId AND saleId = :saleId AND deletedAt IS NULL ORDER BY id")
    fun observeForSale(userId: Long, saleId: Long): Flow<List<SaleLineEntity>>

    @Query("SELECT * FROM sale_lines WHERE userId = :userId AND saleId = :saleId AND deletedAt IS NULL ORDER BY id")
    suspend fun getForSale(userId: Long, saleId: Long): List<SaleLineEntity>

    /**
     * Performance par produit sur `[from, to)` (ventes validées). [ranking] : 0 plus vendus, 1 moins vendus
     * (parmi les produits vendus), 2 plus rentables (produits dont au moins une ligne a un coût connu).
     * Marge brute avant remise = ventes − coût d'achat des seules lignes dont le coût est connu.
     */
    @Query(
        "SELECT l.productId AS productId, MAX(l.productName) AS name, SUM(l.quantityMilli) AS quantityMilli, " +
            "SUM(l.lineTotalMinor) AS revenueMinor, " +
            "CASE WHEN COUNT(l.unitCostMinor) = 0 THEN NULL ELSE SUM(CASE WHEN l.unitCostMinor IS NOT NULL " +
            "THEN l.lineTotalMinor - l.unitCostMinor * l.quantityMilli / 1000 END) END AS grossMarginMinor " +
            "FROM sale_lines l JOIN sales s ON s.id = l.saleId " +
            "WHERE l.userId = :userId AND s.shopId = :shopId AND s.deletedAt IS NULL AND l.deletedAt IS NULL " +
            "AND s.status = 'CONFIRMED' AND s.soldAt >= :fromMillis AND s.soldAt < :toMillis " +
            "GROUP BY l.productId " +
            "HAVING :ranking <> 2 OR COUNT(l.unitCostMinor) > 0 " +
            "ORDER BY CASE WHEN :ranking = 0 THEN SUM(l.quantityMilli) END DESC, " +
            "CASE WHEN :ranking = 1 THEN SUM(l.quantityMilli) END ASC, " +
            "CASE WHEN :ranking = 2 THEN grossMarginMinor END DESC, name, l.productId " +
            "LIMIT :limit"
    )
    fun observeProductStats(userId: Long, shopId: Long, fromMillis: Long, toMillis: Long, ranking: Int, limit: Int): Flow<List<ProductSalesStatRow>>

    /** Purge TOUTES les lignes de [userId] (restauration de sauvegarde, suppression du compte). */
    /** Lecture par id, y compris une ligne supprimée (résolution de références pour la synchronisation). */
    @Query("SELECT * FROM sale_lines WHERE id = :id AND userId = :userId")
    suspend fun getById(id: Long, userId: Long): SaleLineEntity?

    /** Réservé au gestionnaire de synchronisation (voir `ShopSyncHandler`) : `syncId` est unique tous utilisateurs confondus. */
    @Query("SELECT * FROM sale_lines WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): SaleLineEntity?

    /** Insère (id 0) ou met à jour la ligne telle que reçue du serveur. */
    @Upsert
    suspend fun upsert(row: SaleLineEntity): Long

    /** Rattrapage de la synchronisation : lignes jamais enfilées (voir `ShopSyncHandler.enqueueUnsynced`). */
    @Query("SELECT * FROM sale_lines WHERE userId = :userId AND deletedAt IS NULL AND syncId IS NULL ORDER BY id")
    suspend fun getUnsyncedForUser(userId: Long): List<SaleLineEntity>

    /** > 0 : la vente date d'avant la synchronisation et n'a jamais été enfilée (ses lignes n'ont pas de `syncId`). */
    @Query("SELECT COUNT(*) FROM sale_lines WHERE userId = :userId AND saleId = :saleId AND syncId IS NULL")
    suspend fun countUnsyncedForSale(userId: Long, saleId: Long): Int

    /** Attribue l'identité de synchronisation (voir `ShopSyncEnqueuer`) ; sans effet sur `updatedAt`. */
    @Query("UPDATE sale_lines SET syncId = :syncId WHERE id = :id AND userId = :userId")
    suspend fun setSyncId(id: Long, userId: Long, syncId: String)

    @Query("DELETE FROM sale_lines WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}

/** Performance brute d'un produit (voir [SaleLineDao.observeProductStats]). */
data class ProductSalesStatRow(
    val productId: Long,
    val name: String,
    val quantityMilli: Long,
    val revenueMinor: Long,
    val grossMarginMinor: Long?
)
