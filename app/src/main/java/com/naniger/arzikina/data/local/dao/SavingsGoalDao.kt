package com.naniger.arzikina.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.naniger.arzikina.data.local.entity.SavingsGoalEntity
import kotlinx.coroutines.flow.Flow

/**
 * ANCIEN système d'objectifs d'épargne — lecture seule en pratique (voir la doc de
 * [SavingsGoalEntity] et `LegacySavingsGoalMigrator`), plus aucun écran n'y écrit.
 *
 * Voir `data/local/dao/AccountDao` pour le raisonnement sur le filtrage systématique par `userId`.
 *
 * Suppression DOUCE (voir `CategoryDao` pour le même raisonnement complet, déjà appliqué à
 * `categories`) : toutes les lectures ci-dessous filtrent `deletedAt IS NULL`, `deleteById` est
 * remplacé par `softDeleteById`. Seul [deleteAllForUser] (restauration d'une sauvegarde) ignore ce
 * filtre.
 */
@Dao
interface SavingsGoalDao {

    @Query("SELECT * FROM savings_goals WHERE userId = :userId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAllForUser(userId: Long): Flow<List<SavingsGoalEntity>>

    /** Objectifs de l'ANCIEN système encore actifs — voir `LegacySavingsGoalMigrator`, qui les
     * convertit en comptes `SAVINGS_GOAL`. */
    @Query("SELECT * FROM savings_goals WHERE userId = :userId AND deletedAt IS NULL ORDER BY createdAt ASC, id ASC")
    suspend fun getActiveForUser(userId: Long): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals WHERE id = :id AND userId = :userId AND deletedAt IS NULL")
    suspend fun getById(id: Long, userId: Long): SavingsGoalEntity?

    /** Réservé à `SyncEngineImpl` — voir la KDoc de `CategoryDao.getBySyncId` (même raisonnement,
     * volontairement SANS filtre `deletedAt IS NULL` ni `userId`). */
    @Query("SELECT * FROM savings_goals WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): SavingsGoalEntity?

    /** Réservé à [com.naniger.arzikina.data.repository.SyncEngineImpl.enqueueUnsyncedLocalData] — voir
     * la KDoc de `CategoryDao.getUnsyncedForUser` (même raisonnement, `BackupRepositoryImpl` étant
     * ici le seul chemin de contournement connu, aucun seeder par défaut n'existe pour cette
     * entité). */
    @Query("SELECT * FROM savings_goals WHERE userId = :userId AND deletedAt IS NULL AND syncId IS NULL")
    suspend fun getUnsyncedForUser(userId: Long): List<SavingsGoalEntity>

    @Upsert
    suspend fun upsert(goal: SavingsGoalEntity)

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert` : toujours une
     * insertion neuve avec `id = 0L` lors d'une restauration, jamais une mise à jour). */
    @Insert
    suspend fun insertAll(goals: List<SavingsGoalEntity>): List<Long>

    /** Suppression DOUCE (voir la doc de tête) : `deletedAt`/`updatedAt` seulement, même principe
     * que `CategoryDao.softDeleteById`. */
    @Query("UPDATE savings_goals SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND userId = :userId")
    suspend fun softDeleteById(id: Long, userId: Long, deletedAt: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de
     * [userId] — suppression PHYSIQUE assumée (voir la doc de tête). */
    @Query("DELETE FROM savings_goals WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
