package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.SavingsGoalEntity
import kotlinx.coroutines.flow.Flow

/**
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

    @Query("SELECT * FROM savings_goals WHERE id = :id AND userId = :userId AND deletedAt IS NULL")
    suspend fun getById(id: Long, userId: Long): SavingsGoalEntity?

    /** Réservé à `SyncEngineImpl` — voir la KDoc de `CategoryDao.getBySyncId` (même raisonnement,
     * volontairement SANS filtre `deletedAt IS NULL` ni `userId`). */
    @Query("SELECT * FROM savings_goals WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): SavingsGoalEntity?

    /** Réservé à [com.arzikina.ne.data.repository.SyncEngineImpl.enqueueUnsyncedLocalData] — voir
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

    /** [updatedAt] désormais un paramètre explicite (plus un simple `currentAmount + delta` sans
     * horodatage) : une contribution est une écriture comme une autre pour la synchronisation (voir
     * `SavingsGoalRepositoryImpl.addContribution`), elle doit faire progresser `updatedAt` exactement
     * comme `upsert` — sinon le serveur ne la verrait jamais lors du prochain push. */
    @Query("UPDATE savings_goals SET currentAmount = currentAmount + :amountDelta, updatedAt = :updatedAt WHERE id = :id AND userId = :userId")
    suspend fun addContribution(id: Long, amountDelta: Long, userId: Long, updatedAt: Long)

    /** Suppression DOUCE (voir la doc de tête) : `deletedAt`/`updatedAt` seulement, même principe
     * que `CategoryDao.softDeleteById`. */
    @Query("UPDATE savings_goals SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND userId = :userId")
    suspend fun softDeleteById(id: Long, userId: Long, deletedAt: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de
     * [userId] — suppression PHYSIQUE assumée (voir la doc de tête). */
    @Query("DELETE FROM savings_goals WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
