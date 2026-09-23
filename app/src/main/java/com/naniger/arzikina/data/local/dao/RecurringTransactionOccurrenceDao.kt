package com.naniger.arzikina.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.naniger.arzikina.data.local.entity.RecurringTransactionOccurrenceEntity
import kotlinx.coroutines.flow.Flow

/**
 * Voir `data/local/dao/AccountDao` pour le raisonnement sur le filtrage systématique par `userId`.
 *
 * Pas de requête "À venir" ici : contrairement à "À traiter" (statut `OccurrenceStatus.PENDING` déjà
 * en base) et "Historique" (statut déjà décidé), les échéances futures ne sont pas pré-générées en
 * base (voir `RecurringTransactionEntity.nextExecutionDate`, avancée seulement au moment où une
 * occurrence PENDING est réellement créée) — elles sont projetées à la volée par la couche
 * presentation à partir de [RecurringTransactionDao.observeAllForUser], pour ne jamais avoir à purger
 * des lignes "futures" générées par erreur trop tôt.
 *
 * Suppression DOUCE (voir `AccountDao`/`CategoryDao` pour le raisonnement complet, même principe) :
 * toutes les lectures ci-dessous filtrent `deletedAt IS NULL`, [deleteById] est remplacé par
 * [softDeleteById]. Seul [deleteAllForUser] (restauration d'une sauvegarde) ignore ce filtre.
 */
@Dao
interface RecurringTransactionOccurrenceDao {

    /** "À traiter" (voir `PendingOccurrenceViewModel`/écran "Transactions planifiées") : la file
     * d'attente ET le badge du Dashboard partagent cette même requête (voir sa doc dans
     * `RecurringTransactionOccurrenceDao`), pour ne jamais faire diverger leur compte. `'PENDING'`
     * en dur plutôt qu'un paramètre lié : pas de valeur par défaut sur une méthode abstraite de DAO
     * (comportement non garanti par KSP avec `room.generateKotlin`), et le littéral correspond
     * exactement à `OccurrenceStatus.PENDING.name` via `Converters.fromOccurrenceStatus`. */
    @Query("SELECT * FROM recurring_transaction_occurrences WHERE userId = :userId AND status = 'PENDING' AND deletedAt IS NULL ORDER BY scheduledDate ASC")
    fun observePendingForUser(userId: Long): Flow<List<RecurringTransactionOccurrenceEntity>>

    /** "Historique" (voir écran "Transactions planifiées") : occurrences déjà traitées, les plus
     * récentes d'abord. Même raisonnement que [observePendingForUser] pour le littéral `'PENDING'`. */
    @Query("SELECT * FROM recurring_transaction_occurrences WHERE userId = :userId AND status != 'PENDING' AND deletedAt IS NULL ORDER BY processedAt DESC")
    fun observeProcessedForUser(userId: Long): Flow<List<RecurringTransactionOccurrenceEntity>>

    /** TOUS les statuts confondus, contrairement à [observePendingForUser]/[observeProcessedForUser] :
     * utilisée par `BackupRepositoryImpl.exportBackup`, qui a besoin de l'historique complet d'une
     * règle (pas seulement sa file d'attente ou son historique déjà traité pris séparément). */
    @Query("SELECT * FROM recurring_transaction_occurrences WHERE userId = :userId AND deletedAt IS NULL")
    fun observeAllForUser(userId: Long): Flow<List<RecurringTransactionOccurrenceEntity>>

    @Query("SELECT * FROM recurring_transaction_occurrences WHERE id = :id AND userId = :userId AND deletedAt IS NULL")
    suspend fun getById(id: Long, userId: Long): RecurringTransactionOccurrenceEntity?

    /** Lecture ponctuelle (hors `Flow`) : utilisée par
     * `RecurringTransactionRepositoryImpl.deleteRecurringTransaction` pour nettoyer les transactions
     * déjà enregistrées (ACCEPTED/MODIFIED) AVANT de supprimer la règle (voir
     * `RecurringTransactionEntity`, `CASCADE` sur les occurrences elles-mêmes). */
    @Query("SELECT * FROM recurring_transaction_occurrences WHERE recurringTransactionId = :recurringTransactionId AND userId = :userId AND deletedAt IS NULL")
    suspend fun getAllForRecurringTransaction(recurringTransactionId: Long, userId: Long): List<RecurringTransactionOccurrenceEntity>

    /** Garde-fou contre une double génération (voir l'index unique `(recurringTransactionId,
     * scheduledDate)` sur [RecurringTransactionOccurrenceEntity]) : vérifiée AVANT chaque insertion
     * par `generateMissingOccurrences`, en plus de la contrainte base. */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM recurring_transaction_occurrences " +
            "WHERE recurringTransactionId = :recurringTransactionId AND scheduledDate = :scheduledDate)"
    )
    suspend fun existsForDate(recurringTransactionId: Long, scheduledDate: Long): Boolean

    /** Réservé à `SyncEngineImpl` — voir la KDoc de `CategoryDao.getBySyncId` (même raisonnement,
     * volontairement SANS filtre `deletedAt IS NULL` ni `userId`). */
    @Query("SELECT * FROM recurring_transaction_occurrences WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): RecurringTransactionOccurrenceEntity?

    /** Réservé à `SyncEngineImpl.enqueueUnsyncedLocalData` — voir la KDoc de
     * `CategoryDao.getUnsyncedForUser` (même raisonnement). */
    @Query("SELECT * FROM recurring_transaction_occurrences WHERE userId = :userId AND deletedAt IS NULL AND syncId IS NULL")
    suspend fun getUnsyncedForUser(userId: Long): List<RecurringTransactionOccurrenceEntity>

    /** Retourne l'id de la ligne insérée, ou -1 en cas de mise à jour (voir `AccountDao.upsert`). */
    @Upsert
    suspend fun upsert(occurrence: RecurringTransactionOccurrenceEntity): Long

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert` : toujours une
     * insertion neuve avec `id = 0L` lors d'une restauration, jamais une mise à jour). */
    @Insert
    suspend fun insertAll(occurrences: List<RecurringTransactionOccurrenceEntity>): List<Long>

    /** Suppression DOUCE (voir la doc de tête) : `deletedAt`/`updatedAt` seulement, même principe
     * que `AccountDao.softDeleteById`. */
    @Query("UPDATE recurring_transaction_occurrences SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND userId = :userId")
    suspend fun softDeleteById(id: Long, userId: Long, deletedAt: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de [userId]. */
    @Query("DELETE FROM recurring_transaction_occurrences WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
