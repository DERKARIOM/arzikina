package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.RecurringTransactionOccurrenceEntity
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
 */
@Dao
interface RecurringTransactionOccurrenceDao {

    /** "À traiter" (voir `PendingOccurrenceViewModel`/écran "Transactions planifiées") : la file
     * d'attente ET le badge du Dashboard partagent cette même requête (voir sa doc dans
     * `RecurringTransactionOccurrenceDao`), pour ne jamais faire diverger leur compte. `'PENDING'`
     * en dur plutôt qu'un paramètre lié : pas de valeur par défaut sur une méthode abstraite de DAO
     * (comportement non garanti par KSP avec `room.generateKotlin`), et le littéral correspond
     * exactement à `OccurrenceStatus.PENDING.name` via `Converters.fromOccurrenceStatus`. */
    @Query("SELECT * FROM recurring_transaction_occurrences WHERE userId = :userId AND status = 'PENDING' ORDER BY scheduledDate ASC")
    fun observePendingForUser(userId: Long): Flow<List<RecurringTransactionOccurrenceEntity>>

    /** "Historique" (voir écran "Transactions planifiées") : occurrences déjà traitées, les plus
     * récentes d'abord. Même raisonnement que [observePendingForUser] pour le littéral `'PENDING'`. */
    @Query("SELECT * FROM recurring_transaction_occurrences WHERE userId = :userId AND status != 'PENDING' ORDER BY processedAt DESC")
    fun observeProcessedForUser(userId: Long): Flow<List<RecurringTransactionOccurrenceEntity>>

    /** TOUS les statuts confondus, contrairement à [observePendingForUser]/[observeProcessedForUser] :
     * utilisée par `BackupRepositoryImpl.exportBackup`, qui a besoin de l'historique complet d'une
     * règle (pas seulement sa file d'attente ou son historique déjà traité pris séparément). */
    @Query("SELECT * FROM recurring_transaction_occurrences WHERE userId = :userId")
    fun observeAllForUser(userId: Long): Flow<List<RecurringTransactionOccurrenceEntity>>

    @Query("SELECT * FROM recurring_transaction_occurrences WHERE id = :id AND userId = :userId")
    suspend fun getById(id: Long, userId: Long): RecurringTransactionOccurrenceEntity?

    /** Lecture ponctuelle (hors `Flow`) : utilisée par
     * `RecurringTransactionRepositoryImpl.deleteRecurringTransaction` pour nettoyer les transactions
     * déjà enregistrées (ACCEPTED/MODIFIED) AVANT de supprimer la règle (voir
     * `RecurringTransactionEntity`, `CASCADE` sur les occurrences elles-mêmes). */
    @Query("SELECT * FROM recurring_transaction_occurrences WHERE recurringTransactionId = :recurringTransactionId AND userId = :userId")
    suspend fun getAllForRecurringTransaction(recurringTransactionId: Long, userId: Long): List<RecurringTransactionOccurrenceEntity>

    /** Garde-fou contre une double génération (voir l'index unique `(recurringTransactionId,
     * scheduledDate)` sur [RecurringTransactionOccurrenceEntity]) : vérifiée AVANT chaque insertion
     * par `generateMissingOccurrences`, en plus de la contrainte base. */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM recurring_transaction_occurrences " +
            "WHERE recurringTransactionId = :recurringTransactionId AND scheduledDate = :scheduledDate)"
    )
    suspend fun existsForDate(recurringTransactionId: Long, scheduledDate: Long): Boolean

    /** Retourne l'id de la ligne insérée, ou -1 en cas de mise à jour (voir `AccountDao.upsert`). */
    @Upsert
    suspend fun upsert(occurrence: RecurringTransactionOccurrenceEntity): Long

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert` : toujours une
     * insertion neuve avec `id = 0L` lors d'une restauration, jamais une mise à jour). */
    @Insert
    suspend fun insertAll(occurrences: List<RecurringTransactionOccurrenceEntity>): List<Long>

    @Query("DELETE FROM recurring_transaction_occurrences WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: Long, userId: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de [userId]. */
    @Query("DELETE FROM recurring_transaction_occurrences WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
