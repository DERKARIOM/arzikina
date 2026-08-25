package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.FinancialPlanItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * Voir `FinancialPlanDao` pour le raisonnement général.
 *
 * Ces dépenses prévues ne sont PAS synchronisées à cette étape (seule la planification parente
 * l'est, voir `FinancialPlanRepositoryImpl`/`SyncEngineImpl`) — [syncId]/[version] existent déjà en
 * base (colonnes additives, voir `FinancialPlanItemEntity`) mais restent inutilisés ici pour
 * l'instant. [deletedAt] est en revanche déjà exploité : `deleteById` (suppression d'UNE dépense
 * prévue par l'utilisateur) reste un vrai `DELETE`, mais [softDeleteAllForPlan] (cascade explicite
 * depuis `FinancialPlanRepositoryImpl.deletePlan`) doit passer par une suppression douce — d'où le
 * filtre `deletedAt IS NULL` sur toutes les lectures ci-dessous, nécessaire dans les deux cas.
 */
@Dao
interface FinancialPlanItemDao {

    @Query("SELECT * FROM financial_plan_items WHERE planId = :planId AND userId = :userId AND deletedAt IS NULL ORDER BY createdAt ASC")
    fun observeForPlan(planId: Long, userId: Long): Flow<List<FinancialPlanItemEntity>>

    /** Toutes les dépenses prévues de l'utilisateur, TOUTES planifications confondues — utilisé
     * par l'écran liste pour calculer le total prévu/reste de CHAQUE planification sans une
     * requête par planification (voir `FinancialPlansViewModel`, même principe que
     * `BudgetViewModel` avec `TransactionDao.observeAllForUser`). */
    @Query("SELECT * FROM financial_plan_items WHERE userId = :userId AND deletedAt IS NULL")
    fun observeAllForUser(userId: Long): Flow<List<FinancialPlanItemEntity>>

    @Query("SELECT * FROM financial_plan_items WHERE id = :id AND userId = :userId AND deletedAt IS NULL")
    suspend fun getById(id: Long, userId: Long): FinancialPlanItemEntity?

    @Upsert
    suspend fun upsert(item: FinancialPlanItemEntity): Long

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert`). */
    @Insert
    suspend fun insertAll(items: List<FinancialPlanItemEntity>): List<Long>

    @Query("DELETE FROM financial_plan_items WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: Long, userId: Long)

    /** Rattrapage EXPLICITE du cascade SQLite perdu par le passage de `FinancialPlanDao.deleteById`
     * (vrai `DELETE`) à `softDeleteById` (`UPDATE`, voir sa doc) — un `UPDATE` sur `financial_plans`
     * ne déclenche jamais la `ForeignKey.CASCADE` de `financial_plan_items.planId`. Appelée UNIQUEMENT
     * par `FinancialPlanRepositoryImpl.deletePlan`, jamais isolément. */
    @Query("UPDATE financial_plan_items SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE planId = :planId AND userId = :userId")
    suspend fun softDeleteAllForPlan(planId: Long, userId: Long, deletedAt: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de
     * [userId]. */
    @Query("DELETE FROM financial_plan_items WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
