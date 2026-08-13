package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.SavingsGoalEntity
import kotlinx.coroutines.flow.Flow

/** Voir `data/local/dao/AccountDao` pour le raisonnement sur le filtrage systématique par `userId`. */
@Dao
interface SavingsGoalDao {

    @Query("SELECT * FROM savings_goals WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeAllForUser(userId: Long): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE id = :id AND userId = :userId")
    suspend fun getById(id: Long, userId: Long): SavingsGoalEntity?

    @Upsert
    suspend fun upsert(goal: SavingsGoalEntity)

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert` : toujours une
     * insertion neuve avec `id = 0L` lors d'une restauration, jamais une mise à jour). */
    @Insert
    suspend fun insertAll(goals: List<SavingsGoalEntity>): List<Long>

    @Query("UPDATE savings_goals SET currentAmount = currentAmount + :amountDelta WHERE id = :id AND userId = :userId")
    suspend fun addContribution(id: Long, amountDelta: Long, userId: Long)

    @Query("DELETE FROM savings_goals WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: Long, userId: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de [userId]. */
    @Query("DELETE FROM savings_goals WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
