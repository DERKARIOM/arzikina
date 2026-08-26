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
 * Suppression DOUCE (voir `AccountDao`/`CategoryDao` pour le raisonnement complet, même principe) :
 * toutes les lectures ci-dessous filtrent `deletedAt IS NULL`, [deleteById] est remplacé par
 * [softDeleteById] — voir `FinancialPlanRepositoryImpl.deletePlan` pour le rattrapage explicite de
 * la cascade (`financial_plan_items`, ligne par ligne désormais, voir [getAllForPlan]) que SQLite ne
 * peut plus fournir sur un simple `UPDATE`. Seul [deleteAllForUser] (restauration d'une sauvegarde)
 * ignore ce filtre.
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

    /** Lecture ponctuelle (hors `Flow`) : utilisée par `FinancialPlanRepositoryImpl.deletePlan` pour
     * soft-supprimer et enfiler CHAQUE dépense prévue individuellement (chacune avec son propre
     * `syncId`) plutôt que via un `UPDATE` bulk — voir [softDeleteById]. */
    @Query("SELECT * FROM financial_plan_items WHERE planId = :planId AND userId = :userId AND deletedAt IS NULL")
    suspend fun getAllForPlan(planId: Long, userId: Long): List<FinancialPlanItemEntity>

    /** Réservé à `SyncEngineImpl` — voir la KDoc de `CategoryDao.getBySyncId` (même raisonnement,
     * volontairement SANS filtre `deletedAt IS NULL` ni `userId`). */
    @Query("SELECT * FROM financial_plan_items WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): FinancialPlanItemEntity?

    /** Réservé aux résolveurs `*SyncEnqueuer`/fonctions de résolution privées — voir la KDoc de
     * `AccountDao.getByIdIncludingDeleted` (même raisonnement : une dépense prévue peut être enfilée
     * APRÈS que sa planification parente a déjà été soft-supprimée dans la même cascade, voir
     * `FinancialPlanRepositoryImpl.deletePlan`). */
    @Query("SELECT * FROM financial_plan_items WHERE id = :id AND userId = :userId")
    suspend fun getByIdIncludingDeleted(id: Long, userId: Long): FinancialPlanItemEntity?

    /** Réservé à `SyncEngineImpl.enqueueUnsyncedLocalData` — voir la KDoc de
     * `CategoryDao.getUnsyncedForUser` (même raisonnement). */
    @Query("SELECT * FROM financial_plan_items WHERE userId = :userId AND deletedAt IS NULL AND syncId IS NULL")
    suspend fun getUnsyncedForUser(userId: Long): List<FinancialPlanItemEntity>

    /** Retourne l'id de la ligne insérée, ou -1 en cas de mise à jour (voir `AccountDao.upsert`). */
    @Upsert
    suspend fun upsert(item: FinancialPlanItemEntity): Long

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert`). */
    @Insert
    suspend fun insertAll(items: List<FinancialPlanItemEntity>): List<Long>

    /** Suppression DOUCE (voir la doc de tête) : `deletedAt`/`updatedAt` seulement, même principe
     * que `AccountDao.softDeleteById`. */
    @Query("UPDATE financial_plan_items SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND userId = :userId")
    suspend fun softDeleteById(id: Long, userId: Long, deletedAt: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de
     * [userId]. */
    @Query("DELETE FROM financial_plan_items WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
