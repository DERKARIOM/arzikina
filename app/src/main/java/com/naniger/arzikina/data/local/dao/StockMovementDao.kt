package com.naniger.arzikina.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.naniger.arzikina.data.local.entity.StockMovementEntity
import kotlinx.coroutines.flow.Flow

/**
 * Accès à la table `stock_movements` (mouvements IMMUABLES : jamais de mise à jour ni de suppression
 * hors purge de compte). Filtrage systématique par `userId` (voir `AccountDao`).
 */
@Dao
interface StockMovementDao {

    /** Plus récents d'abord ; `id` départage les mouvements simultanés. */
    @Query(
        "SELECT * FROM stock_movements WHERE userId = :userId AND productId = :productId AND deletedAt IS NULL " +
            "ORDER BY occurredAt DESC, id DESC LIMIT :limit"
    )
    fun observeForProduct(userId: Long, productId: Long, limit: Int): Flow<List<StockMovementEntity>>

    /** Somme des mouvements : doit toujours égaler `products.stockMilli` (contrôle d'intégrité). */
    @Query("SELECT COALESCE(SUM(deltaMilli), 0) FROM stock_movements WHERE userId = :userId AND productId = :productId AND deletedAt IS NULL")
    suspend fun sumDelta(userId: Long, productId: Long): Long

    /** Retourne l'id de la ligne insérée. */
    @Insert
    suspend fun insert(movement: StockMovementEntity): Long

    /** Purge TOUTES les lignes de [userId] (restauration de sauvegarde, suppression du compte). */
    /** Lecture par id, y compris une ligne supprimée (résolution de références pour la synchronisation). */
    @Query("SELECT * FROM stock_movements WHERE id = :id AND userId = :userId")
    suspend fun getById(id: Long, userId: Long): StockMovementEntity?

    /** Réservé au gestionnaire de synchronisation (voir `ShopSyncHandler`) : `syncId` est unique tous utilisateurs confondus. */
    @Query("SELECT * FROM stock_movements WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): StockMovementEntity?

    /** Insère (id 0) ou met à jour la ligne telle que reçue du serveur. */
    @Upsert
    suspend fun upsert(row: StockMovementEntity): Long

    /** Rattrapage de la synchronisation : lignes jamais enfilées (voir `ShopSyncHandler.enqueueUnsynced`). */
    @Query("SELECT * FROM stock_movements WHERE userId = :userId AND deletedAt IS NULL AND syncId IS NULL ORDER BY id")
    suspend fun getUnsyncedForUser(userId: Long): List<StockMovementEntity>

    /** Attribue l'identité de synchronisation (voir `ShopSyncEnqueuer`) ; sans effet sur `updatedAt`. */
    @Query("UPDATE stock_movements SET syncId = :syncId WHERE id = :id AND userId = :userId")
    suspend fun setSyncId(id: Long, userId: Long, syncId: String)

    @Query("DELETE FROM stock_movements WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
