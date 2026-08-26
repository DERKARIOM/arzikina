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
 * Suppression DOUCE (voir `CategoryDao` pour le raisonnement complet, même principe) : toutes les
 * lectures ci-dessous filtrent `deletedAt IS NULL`, `deleteById` est remplacé par `softDeleteById`
 * — voir `FinancialPlanRepositoryImpl.deletePlan` pour le rattrapage explicite du cascade que
 * SQLite ne peut plus fournir sur un simple `UPDATE` (contrairement à un vrai `DELETE`, voir
 * `FinancialPlanItemDao.getAllForPlan`/`softDeleteById`, appelés ligne par ligne depuis l'étape 21).
 * Seul [deleteAllForUser] (restauration d'une sauvegarde) ignore ce filtre.
 */
@Dao
interface FinancialPlanDao {

    @Query("SELECT * FROM financial_plans WHERE userId = :userId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAllForUser(userId: Long): Flow<List<FinancialPlanEntity>>

    @Query("SELECT * FROM financial_plans WHERE id = :id AND userId = :userId AND deletedAt IS NULL")
    suspend fun getById(id: Long, userId: Long): FinancialPlanEntity?

    /** Réservé à `SyncEngineImpl` — voir la KDoc de `CategoryDao.getBySyncId` (même raisonnement,
     * volontairement SANS filtre `deletedAt IS NULL` ni `userId`). */
    @Query("SELECT * FROM financial_plans WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): FinancialPlanEntity?

    /** Réservé aux résolveurs `*SyncEnqueuer`/fonctions de résolution privées — voir la KDoc de
     * `AccountDao.getByIdIncludingDeleted` (même raisonnement : une dépense prévue
     * [com.arzikina.ne.data.local.entity.FinancialPlanItemEntity] peut être enfilée APRÈS que sa
     * planification parente a déjà été soft-supprimée dans la même cascade, voir
     * `FinancialPlanRepositoryImpl.deletePlan` — bug de timing identique à celui corrigé à l'étape
     * 19.5b pour `AccountDao`/`CategoryDao`/`TransactionDao`/`PersonDao`/`LoanDao`). */
    @Query("SELECT * FROM financial_plans WHERE id = :id AND userId = :userId")
    suspend fun getByIdIncludingDeleted(id: Long, userId: Long): FinancialPlanEntity?

    /** Réservé à `SyncEngineImpl.enqueueUnsyncedLocalData` — voir la KDoc de
     * `CategoryDao.getUnsyncedForUser` (même raisonnement ; `BackupRepositoryImpl` étant ici le
     * seul chemin de contournement connu, aucun seeder par défaut n'existe pour cette entité). */
    @Query("SELECT * FROM financial_plans WHERE userId = :userId AND deletedAt IS NULL AND syncId IS NULL")
    suspend fun getUnsyncedForUser(userId: Long): List<FinancialPlanEntity>

    /** Retourne l'id de la ligne insérée, ou -1 en cas de mise à jour (voir `AccountDao.upsert`). */
    @Upsert
    suspend fun upsert(plan: FinancialPlanEntity): Long

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert` : toujours une
     * insertion neuve avec `id = 0L` lors d'une restauration, jamais une mise à jour). */
    @Insert
    suspend fun insertAll(plans: List<FinancialPlanEntity>): List<Long>

    /** Suppression DOUCE (voir la doc de tête) : `deletedAt`/`updatedAt` seulement, même principe
     * que `CategoryDao.softDeleteById`. Ne touche PAS `financial_plan_items` — voir
     * `FinancialPlanRepositoryImpl.deletePlan`, qui soft-supprime et enfile explicitement chaque
     * dépense prévue juste après (ligne par ligne, voir `FinancialPlanItemDao.getAllForPlan`), la
     * cascade SQLite ne se déclenchant que sur un vrai `DELETE`. */
    @Query("UPDATE financial_plans SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND userId = :userId")
    suspend fun softDeleteById(id: Long, userId: Long, deletedAt: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de
     * [userId] — suppression PHYSIQUE assumée (voir la doc de tête), pas une suppression métier. */
    @Query("DELETE FROM financial_plans WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
