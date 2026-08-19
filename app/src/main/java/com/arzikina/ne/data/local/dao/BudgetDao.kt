package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

/** Voir `data/local/dao/AccountDao` pour le raisonnement sur le filtrage systématique par `userId`. */
@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeAllForUser(userId: Long): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id AND userId = :userId")
    suspend fun getById(id: Long, userId: Long): BudgetEntity?

    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert` : toujours une
     * insertion neuve avec `id = 0L` lors d'une restauration, jamais une mise à jour). */
    @Insert
    suspend fun insertAll(budgets: List<BudgetEntity>): List<Long>

    @Query("DELETE FROM budgets WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: Long, userId: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de [userId]. */
    @Query("DELETE FROM budgets WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
