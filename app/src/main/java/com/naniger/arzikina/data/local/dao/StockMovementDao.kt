package com.naniger.arzikina.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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
    @Query("SELECT COALESCE(SUM(deltaMilli), 0) FROM stock_movements WHERE userId = :userId AND productId = :productId")
    suspend fun sumDelta(userId: Long, productId: Long): Long

    /** Retourne l'id de la ligne insérée. */
    @Insert
    suspend fun insert(movement: StockMovementEntity): Long

    /** Purge TOUTES les lignes de [userId] (restauration de sauvegarde, suppression du compte). */
    @Query("DELETE FROM stock_movements WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
