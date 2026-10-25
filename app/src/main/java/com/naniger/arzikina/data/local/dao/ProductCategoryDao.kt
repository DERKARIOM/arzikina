package com.naniger.arzikina.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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
    @Query("DELETE FROM product_categories WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
