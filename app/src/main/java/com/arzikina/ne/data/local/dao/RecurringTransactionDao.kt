package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.RecurringTransactionEntity
import kotlinx.coroutines.flow.Flow

/** Voir `data/local/dao/AccountDao` pour le raisonnement sur le filtrage systématique par `userId`. */
@Dao
interface RecurringTransactionDao {

    @Query("SELECT * FROM recurring_transactions WHERE userId = :userId ORDER BY nextExecutionDate ASC")
    fun observeAllForUser(userId: Long): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions WHERE id = :id AND userId = :userId")
    suspend fun getById(id: Long, userId: Long): RecurringTransactionEntity?

    /** Lecture ponctuelle (hors `Flow`) : utilisée par
     * `RecurringTransactionRepositoryImpl.generateMissingOccurrences`, appelée à l'ouverture de
     * l'app (et plus tard par le `Worker` périodique) pour parcourir toutes les règles actives d'un
     * utilisateur et générer leurs occurrences manquantes. */
    @Query("SELECT * FROM recurring_transactions WHERE userId = :userId AND isActive = 1")
    suspend fun getAllActiveForUser(userId: Long): List<RecurringTransactionEntity>

    /** Retourne l'id de la ligne insérée, ou -1 en cas de mise à jour (voir `AccountDao.upsert`). */
    @Upsert
    suspend fun upsert(recurringTransaction: RecurringTransactionEntity): Long

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert` : toujours une
     * insertion neuve avec `id = 0L` lors d'une restauration, jamais une mise à jour). */
    @Insert
    suspend fun insertAll(recurringTransactions: List<RecurringTransactionEntity>): List<Long>

    /** Supprime aussi, en cascade SQLite, tout l'historique d'occurrences de cette règle (voir
     * `RecurringTransactionOccurrenceEntity`). Les transactions déjà enregistrées à partir de ses
     * occurrences ACCEPTED/MODIFIED ne sont volontairement PAS supprimées : elles deviennent des
     * transactions normales, indépendantes de la règle qui les a créées. */
    @Query("DELETE FROM recurring_transactions WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: Long, userId: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de [userId]. */
    @Query("DELETE FROM recurring_transactions WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
