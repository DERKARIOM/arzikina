package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.LoanPaymentEntity
import kotlinx.coroutines.flow.Flow

/** Voir `data/local/dao/AccountDao` pour le raisonnement sur le filtrage systématique par `userId`. */
@Dao
interface LoanPaymentDao {

    @Query("SELECT * FROM loan_payments WHERE loanId = :loanId AND userId = :userId ORDER BY date DESC")
    fun observeForLoan(loanId: Long, userId: Long): Flow<List<LoanPaymentEntity>>

    @Query("SELECT * FROM loan_payments WHERE id = :id AND userId = :userId")
    suspend fun getById(id: Long, userId: Long): LoanPaymentEntity?

    /** Lecture ponctuelle (hors `Flow`) : utilisée par `LoanRepositoryImpl.deleteLoan` et
     * `PersonRepositoryImpl.deletePerson` pour retrouver les transactions à nettoyer AVANT une
     * suppression en cascade (voir `LoanDao.deleteById`). */
    @Query("SELECT * FROM loan_payments WHERE loanId = :loanId AND userId = :userId")
    suspend fun getAllForLoan(loanId: Long, userId: Long): List<LoanPaymentEntity>

    /** Lecture ponctuelle (hors `Flow`) : utilisée par `AccountRepositoryImpl.deleteAccount` pour
     * repérer les remboursements enregistrés sur CE compte alors que le prêt/emprunt parent utilise
     * un compte DIFFÉRENT (voir `LoanPaymentEntity.accountId`, indépendant de `LoanEntity.accountId`)
     * — leur suppression en cascade laisserait le prêt parent avec un montant remboursé/statut
     * périmés si son montant n'était pas recalculé avant. */
    @Query("SELECT * FROM loan_payments WHERE accountId = :accountId AND userId = :userId")
    suspend fun getAllForAccount(accountId: Long, userId: Long): List<LoanPaymentEntity>

    /** Retourne l'id de la ligne insérée, ou -1 en cas de mise à jour (voir `AccountDao.upsert`). */
    @Upsert
    suspend fun upsert(payment: LoanPaymentEntity): Long

    @Upsert
    suspend fun insertAll(payments: List<LoanPaymentEntity>)

    /** Supprime UNIQUEMENT la ligne de remboursement — la transaction liée doit être supprimée
     * séparément par l'appelant (voir `LoanRepositoryImpl.deletePayment`). */
    @Query("DELETE FROM loan_payments WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: Long, userId: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de [userId]. */
    @Query("DELETE FROM loan_payments WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)

    /** Retrouve le prêt/emprunt dont un REMBOURSEMENT correspond à cette transaction — même
     * raisonnement que `LoanDao.findIdByTransactionId`. */
    @Query("SELECT loanId FROM loan_payments WHERE transactionId = :transactionId AND userId = :userId LIMIT 1")
    suspend fun findLoanIdByTransactionId(transactionId: Long, userId: Long): Long?
}
