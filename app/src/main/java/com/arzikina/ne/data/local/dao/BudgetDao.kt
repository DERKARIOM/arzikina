package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: Long): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId")
    suspend fun getByCategoryId(categoryId: Long): BudgetEntity?

    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    /** Utilisé uniquement par la restauration d'une sauvegarde (remplacement complet). */
    @Upsert
    suspend fun insertAll(budgets: List<BudgetEntity>)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde (remplacement complet). */
    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}
