package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.SavingsGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsGoalDao {

    @Query("SELECT * FROM savings_goals ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getById(id: Long): SavingsGoalEntity?

    @Upsert
    suspend fun upsert(goal: SavingsGoalEntity)

    /** Utilisé uniquement par la restauration d'une sauvegarde (remplacement complet). */
    @Upsert
    suspend fun insertAll(goals: List<SavingsGoalEntity>)

    @Query("UPDATE savings_goals SET currentAmount = currentAmount + :amountDelta WHERE id = :id")
    suspend fun addContribution(id: Long, amountDelta: Long)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde (remplacement complet). */
    @Query("DELETE FROM savings_goals")
    suspend fun deleteAll()
}
