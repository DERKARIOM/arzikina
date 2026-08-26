package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.LoanPaymentEntity
import kotlinx.coroutines.flow.Flow

/**
 * Voir `data/local/dao/AccountDao` pour le raisonnement sur le filtrage systématique par `userId`.
 *
 * Suppression DOUCE (voir `LoanDao` pour le raisonnement complet, même principe) : toutes les
 * lectures ci-dessous filtrent `deletedAt IS NULL`, [deleteById] est remplacé par [softDeleteById].
 * Seul [deleteAllForUser] (restauration d'une sauvegarde) ignore ce filtre.
 */
@Dao
interface LoanPaymentDao {

    @Query("SELECT * FROM loan_payments WHERE loanId = :loanId AND userId = :userId AND deletedAt IS NULL ORDER BY date DESC")
    fun observeForLoan(loanId: Long, userId: Long): Flow<List<LoanPaymentEntity>>

    @Query("SELECT * FROM loan_payments WHERE id = :id AND userId = :userId AND deletedAt IS NULL")
    suspend fun getById(id: Long, userId: Long): LoanPaymentEntity?

    /** Lecture ponctuelle (hors `Flow`) : utilisée par `LoanRepositoryImpl.deleteLoan` et
     * `PersonRepositoryImpl.deletePerson` pour retrouver les transactions à nettoyer AVANT une
     * suppression en cascade (voir `LoanDao.softDeleteById`). */
    @Query("SELECT * FROM loan_payments WHERE loanId = :loanId AND userId = :userId AND deletedAt IS NULL")
    suspend fun getAllForLoan(loanId: Long, userId: Long): List<LoanPaymentEntity>

    /** Lecture ponctuelle (hors `Flow`) : utilisée par `AccountRepositoryImpl.deleteAccount` pour
     * repérer les remboursements enregistrés sur CE compte alors que le prêt/emprunt parent utilise
     * un compte DIFFÉRENT (voir `LoanPaymentEntity.accountId`, indépendant de `LoanEntity.accountId`)
     * — leur suppression en cascade laisserait le prêt parent avec un montant remboursé/statut
     * périmés si son montant n'était pas recalculé avant. */
    @Query("SELECT * FROM loan_payments WHERE accountId = :accountId AND userId = :userId AND deletedAt IS NULL")
    suspend fun getAllForAccount(accountId: Long, userId: Long): List<LoanPaymentEntity>

    /** Réservé à `SyncEngineImpl` — voir la KDoc de `CategoryDao.getBySyncId` (même raisonnement,
     * volontairement SANS filtre `deletedAt IS NULL` ni `userId`). */
    @Query("SELECT * FROM loan_payments WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): LoanPaymentEntity?

    /** Réservé à `SyncEngineImpl.enqueueUnsyncedLocalData` — voir la KDoc de
     * `CategoryDao.getUnsyncedForUser` (même raisonnement). */
    @Query("SELECT * FROM loan_payments WHERE userId = :userId AND deletedAt IS NULL AND syncId IS NULL")
    suspend fun getUnsyncedForUser(userId: Long): List<LoanPaymentEntity>

    /** Retourne l'id de la ligne insérée, ou -1 en cas de mise à jour (voir `AccountDao.upsert`). */
    @Upsert
    suspend fun upsert(payment: LoanPaymentEntity): Long

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert` : toujours une
     * insertion neuve avec `id = 0L` lors d'une restauration, jamais une mise à jour). */
    @Insert
    suspend fun insertAll(payments: List<LoanPaymentEntity>): List<Long>

    /** Suppression DOUCE (voir la doc de tête) : `deletedAt`/`updatedAt` seulement — la transaction
     * liée doit être supprimée séparément par l'appelant (voir `LoanRepositoryImpl.deletePayment`). */
    @Query("UPDATE loan_payments SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND userId = :userId")
    suspend fun softDeleteById(id: Long, userId: Long, deletedAt: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de [userId]. */
    @Query("DELETE FROM loan_payments WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)

    /** Retrouve le prêt/emprunt dont un REMBOURSEMENT correspond à cette transaction — même
     * raisonnement que `LoanDao.findIdByTransactionId`. */
    @Query("SELECT loanId FROM loan_payments WHERE transactionId = :transactionId AND userId = :userId AND deletedAt IS NULL LIMIT 1")
    suspend fun findLoanIdByTransactionId(transactionId: Long, userId: Long): Long?
}
