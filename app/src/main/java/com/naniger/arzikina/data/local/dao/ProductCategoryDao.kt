package com.naniger.arzikina.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.naniger.arzikina.data.local.entity.ProductCategoryEntity
import kotlinx.coroutines.flow.Flow

/** Accès à la table `product_categories`. Filtrage systématique par `userId` (voir `AccountDao`). */
@Dao
interface ProductCategoryDao {

    @Query(
        "SELECT * FROM product_categories WHERE userId = :userId AND shopId = :shopId AND deletedAt IS NULL " +
            "ORDER BY name COLLATE NOCASE, id"
    )
    fun observeForShop(userId: Long, shopId: Long): Flow<List<ProductCategoryEntity>>

    /** Catégorie de ce nom (casse ignorée), `null` si aucune. */
    @Query(
        "SELECT * FROM product_categories WHERE userId = :userId AND shopId = :shopId AND deletedAt IS NULL " +
            "AND name = :name COLLATE NOCASE LIMIT 1"
    )
    suspend fun findByName(userId: Long, shopId: Long, name: String): ProductCategoryEntity?

    /** Retourne l'id de la ligne insérée. */
    @Insert
    suspend fun insert(category: ProductCategoryEntity): Long

    /** Purge TOUTES les lignes de [userId] (restauration de sauvegarde, suppression du compte). */
    /** Lecture par id, y compris une ligne supprimée (résolution de références pour la synchronisation). */
    @Query("SELECT * FROM product_categories WHERE id = :id AND userId = :userId")
    suspend fun getById(id: Long, userId: Long): ProductCategoryEntity?

    /** Réservé au gestionnaire de synchronisation (voir `ShopSyncHandler`) : `syncId` est unique tous utilisateurs confondus. */
    @Query("SELECT * FROM product_categories WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): ProductCategoryEntity?

    /** Insère (id 0) ou met à jour la ligne telle que reçue du serveur. */
    @Upsert
    suspend fun upsert(row: ProductCategoryEntity): Long

    /** Rattrapage de la synchronisation : lignes jamais enfilées (voir `ShopSyncHandler.enqueueUnsynced`). */
    @Query("SELECT * FROM product_categories WHERE userId = :userId AND deletedAt IS NULL AND syncId IS NULL ORDER BY id")
    suspend fun getUnsyncedForUser(userId: Long): List<ProductCategoryEntity>

    /** Attribue l'identité de synchronisation (voir `ShopSyncEnqueuer`) ; sans effet sur `updatedAt`. */
    @Query("UPDATE product_categories SET syncId = :syncId WHERE id = :id AND userId = :userId")
    suspend fun setSyncId(id: Long, userId: Long, syncId: String)

    @Query("DELETE FROM product_categories WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
