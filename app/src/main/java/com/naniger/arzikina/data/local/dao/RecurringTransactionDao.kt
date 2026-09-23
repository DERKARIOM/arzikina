package com.naniger.arzikina.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.naniger.arzikina.data.local.entity.RecurringTransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Voir `data/local/dao/AccountDao` pour le raisonnement sur le filtrage systématique par `userId`.
 *
 * Suppression DOUCE (voir `AccountDao`/`CategoryDao` pour le raisonnement complet, même principe) :
 * toutes les lectures ci-dessous filtrent `deletedAt IS NULL`, [deleteById] est remplacé par
 * [softDeleteById] — voir `RecurringTransactionRepositoryImpl.deleteRecurringTransaction` pour le
 * rattrapage explicite de la cascade (`recurring_transaction_occurrences`) que SQLite ne peut plus
 * fournir sur un simple `UPDATE`. Seul [deleteAllForUser] (restauration d'une sauvegarde) ignore ce
 * filtre.
 */
@Dao
interface RecurringTransactionDao {

    @Query("SELECT * FROM recurring_transactions WHERE userId = :userId AND deletedAt IS NULL ORDER BY nextExecutionDate ASC")
    fun observeAllForUser(userId: Long): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions WHERE id = :id AND userId = :userId AND deletedAt IS NULL")
    suspend fun getById(id: Long, userId: Long): RecurringTransactionEntity?

    /** Lecture ponctuelle (hors `Flow`) : utilisée par
     * `RecurringTransactionRepositoryImpl.generateMissingOccurrences`, appelée à l'ouverture de
     * l'app (et plus tard par le `Worker` périodique) pour parcourir toutes les règles actives d'un
     * utilisateur et générer leurs occurrences manquantes. */
    @Query("SELECT * FROM recurring_transactions WHERE userId = :userId AND isActive = 1 AND deletedAt IS NULL")
    suspend fun getAllActiveForUser(userId: Long): List<RecurringTransactionEntity>

    /** Réservé à `SyncEngineImpl` — voir la KDoc de `CategoryDao.getBySyncId` (même raisonnement,
     * volontairement SANS filtre `deletedAt IS NULL` ni `userId`). */
    @Query("SELECT * FROM recurring_transactions WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): RecurringTransactionEntity?

    /** Réservé aux résolveurs `*SyncEnqueuer`/fonctions de résolution privées — voir la KDoc de
     * `AccountDao.getByIdIncludingDeleted` (même raisonnement : une occurrence peut être enfilée
     * APRÈS que sa règle parente a déjà été soft-supprimée dans la même cascade, voir
     * `RecurringTransactionRepositoryImpl.deleteRecurringTransaction`). */
    @Query("SELECT * FROM recurring_transactions WHERE id = :id AND userId = :userId")
    suspend fun getByIdIncludingDeleted(id: Long, userId: Long): RecurringTransactionEntity?

    /** Réservé à `SyncEngineImpl.enqueueUnsyncedLocalData` — voir la KDoc de
     * `CategoryDao.getUnsyncedForUser` (même raisonnement). */
    @Query("SELECT * FROM recurring_transactions WHERE userId = :userId AND deletedAt IS NULL AND syncId IS NULL")
    suspend fun getUnsyncedForUser(userId: Long): List<RecurringTransactionEntity>

    /** Retourne l'id de la ligne insérée, ou -1 en cas de mise à jour (voir `AccountDao.upsert`). */
    @Upsert
    suspend fun upsert(recurringTransaction: RecurringTransactionEntity): Long

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert` : toujours une
     * insertion neuve avec `id = 0L` lors d'une restauration, jamais une mise à jour). */
    @Insert
    suspend fun insertAll(recurringTransactions: List<RecurringTransactionEntity>): List<Long>

    /** Suppression DOUCE (voir la doc de tête) : `deletedAt`/`updatedAt` seulement, même principe
     * que `AccountDao.softDeleteById`. Ne touche PAS `recurring_transaction_occurrences` — voir
     * `RecurringTransactionRepositoryImpl.deleteRecurringTransaction`, qui supprime explicitement
     * (suppression DOUCE désormais, étape 20) tout l'historique d'occurrences AVANT d'appeler cette
     * méthode : la cascade SQLite `recurringTransactionId` ne se déclenche que sur un vrai `DELETE`,
     * jamais sur cet `UPDATE`. */
    @Query("UPDATE recurring_transactions SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND userId = :userId")
    suspend fun softDeleteById(id: Long, userId: Long, deletedAt: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de [userId]. */
    @Query("DELETE FROM recurring_transactions WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
