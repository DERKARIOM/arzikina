package com.naniger.arzikina.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.naniger.arzikina.data.local.entity.CartDraftEntity
import com.naniger.arzikina.data.local.entity.CartLineEntity

/**
 * Accès aux tables `cart_drafts` et `cart_lines`. Filtrage systématique par `userId` ET `shopId`. Le panier
 * est toujours remplacé en bloc (voir `CartLocalDataSource.save`, dans une transaction) : pas de mise à jour
 * ligne par ligne.
 */
@Dao
interface CartDao {

    @Query("SELECT * FROM cart_drafts WHERE userId = :userId AND shopId = :shopId")
    suspend fun getDraft(userId: Long, shopId: Long): CartDraftEntity?

    @Query("SELECT * FROM cart_lines WHERE userId = :userId AND shopId = :shopId ORDER BY position")
    suspend fun getLines(userId: Long, shopId: Long): List<CartLineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(draft: CartDraftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLines(lines: List<CartLineEntity>)

    @Query("DELETE FROM cart_drafts WHERE userId = :userId AND shopId = :shopId")
    suspend fun deleteDraft(userId: Long, shopId: Long)

    @Query("DELETE FROM cart_lines WHERE userId = :userId AND shopId = :shopId")
    suspend fun deleteLines(userId: Long, shopId: Long)

    /** Purge TOUS les paniers de [userId] (restauration de sauvegarde, suppression du compte). */
    @Query("DELETE FROM cart_drafts WHERE userId = :userId")
    suspend fun deleteAllDraftsForUser(userId: Long)

    @Query("DELETE FROM cart_lines WHERE userId = :userId")
    suspend fun deleteAllLinesForUser(userId: Long)
}
