package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.CategoryEntity
import com.arzikina.ne.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

/** Voir `data/local/dao/AccountDao` pour le raisonnement sur le filtrage systématique par `userId`. */
@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY name ASC")
    fun observeAllForUser(userId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type AND userId = :userId ORDER BY name ASC")
    fun observeByTypeForUser(type: TransactionType, userId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id AND userId = :userId")
    suspend fun getById(id: Long, userId: Long): CategoryEntity?

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Upsert
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: Long, userId: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de [userId]. */
    @Query("DELETE FROM categories WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
