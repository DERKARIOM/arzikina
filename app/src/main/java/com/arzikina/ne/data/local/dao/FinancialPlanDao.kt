package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.FinancialPlanEntity
import kotlinx.coroutines.flow.Flow

/**
 * Voir `data/local/dao/AccountDao` pour le raisonnement sur le filtrage systématique par `userId`.
 *
 * Volontairement minimal pour cette étape (même principe que `LoanDao` à son introduction) : le
 * tri par nom (plutôt que par date) sera revu si un besoin réel apparaît à un écran futur.
 */
@Dao
interface FinancialPlanDao {

    @Query("SELECT * FROM financial_plans WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeAllForUser(userId: Long): Flow<List<FinancialPlanEntity>>

    @Query("SELECT * FROM financial_plans WHERE id = :id AND userId = :userId")
    suspend fun getById(id: Long, userId: Long): FinancialPlanEntity?

    /** Retourne l'id de la ligne insérée, ou -1 en cas de mise à jour (voir `AccountDao.upsert`). */
    @Upsert
    suspend fun upsert(plan: FinancialPlanEntity): Long

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert` : toujours une
     * insertion neuve avec `id = 0L` lors d'une restauration, jamais une mise à jour). */
    @Insert
    suspend fun insertAll(plans: List<FinancialPlanEntity>): List<Long>

    /** Supprime aussi, en cascade SQLite, toutes les dépenses prévues de cette planification (voir
     * `FinancialPlanItemEntity`). N'affecte AUCUNE transaction réelle (voir la doc de
     * `FinancialPlanRepository.deletePlan`). */
    @Query("DELETE FROM financial_plans WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: Long, userId: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de
     * [userId]. */
    @Query("DELETE FROM financial_plans WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
