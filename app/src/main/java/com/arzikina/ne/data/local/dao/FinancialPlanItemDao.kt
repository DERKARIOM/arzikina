package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.FinancialPlanItemEntity
import kotlinx.coroutines.flow.Flow

/** Voir `FinancialPlanDao` pour le raisonnement général. */
@Dao
interface FinancialPlanItemDao {

    @Query("SELECT * FROM financial_plan_items WHERE planId = :planId AND userId = :userId ORDER BY createdAt ASC")
    fun observeForPlan(planId: Long, userId: Long): Flow<List<FinancialPlanItemEntity>>

    /** Toutes les dépenses prévues de l'utilisateur, TOUTES planifications confondues — utilisé
     * par l'écran liste pour calculer le total prévu/reste de CHAQUE planification sans une
     * requête par planification (voir `FinancialPlansViewModel`, même principe que
     * `BudgetViewModel` avec `TransactionDao.observeAllForUser`). */
    @Query("SELECT * FROM financial_plan_items WHERE userId = :userId")
    fun observeAllForUser(userId: Long): Flow<List<FinancialPlanItemEntity>>

    @Query("SELECT * FROM financial_plan_items WHERE id = :id AND userId = :userId")
    suspend fun getById(id: Long, userId: Long): FinancialPlanItemEntity?

    @Upsert
    suspend fun upsert(item: FinancialPlanItemEntity): Long

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert`). */
    @Insert
    suspend fun insertAll(items: List<FinancialPlanItemEntity>): List<Long>

    @Query("DELETE FROM financial_plan_items WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: Long, userId: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de
     * [userId]. */
    @Query("DELETE FROM financial_plan_items WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
