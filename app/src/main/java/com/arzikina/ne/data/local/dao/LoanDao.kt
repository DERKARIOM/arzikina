package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.LoanEntity
import kotlinx.coroutines.flow.Flow

/**
 * Voir `data/local/dao/AccountDao` pour le raisonnement sur le filtrage systématique par `userId`.
 *
 * Volontairement minimal pour l'instant (même principe que `TransactionDao`) : les besoins de
 * recherche/filtre (par personne, par statut, par période) seront ajoutés ici à l'étape "Recherche
 * et filtres" du plan, au fur et à mesure des écrans qui en ont réellement besoin.
 *
 * Suppression DOUCE (voir `AccountDao`/`CategoryDao` pour le raisonnement complet, même principe) :
 * toutes les lectures ci-dessous filtrent `deletedAt IS NULL`, [deleteById] est remplacé par
 * [softDeleteById] — voir `LoanRepositoryImpl.deleteLoan`/`AccountRepositoryImpl.deleteAccount`/
 * `PersonRepositoryImpl.deletePerson` (étape 19.4/19.5) pour le rattrapage explicite des cascades
 * (`loan_payments`) que SQLite ne peut plus fournir sur un simple `UPDATE`. Seul
 * [deleteAllForUser] (restauration d'une sauvegarde) ignore ce filtre.
 */
@Dao
interface LoanDao {

    @Query("SELECT * FROM loans WHERE userId = :userId AND deletedAt IS NULL ORDER BY dueDate ASC")
    fun observeAllForUser(userId: Long): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE id = :id AND userId = :userId AND deletedAt IS NULL")
    suspend fun getById(id: Long, userId: Long): LoanEntity?

    /** Lecture ponctuelle (hors `Flow`) : utilisée par `PersonRepositoryImpl.deletePerson` pour
     * nettoyer les transactions liées AVANT de supprimer la personne (voir `PersonDao.deleteById`). */
    @Query("SELECT * FROM loans WHERE personId = :personId AND userId = :userId AND deletedAt IS NULL")
    suspend fun getAllForPerson(personId: Long, userId: Long): List<LoanEntity>

    /** Lecture ponctuelle (hors `Flow`) : utilisée par `AccountRepositoryImpl.deleteAccount` pour
     * nettoyer les transactions liées (décaissement + remboursements, même sur un autre compte)
     * AVANT que la suppression du compte ne cascade sur ces prêts/emprunts (voir
     * `LoanEntity.accountId`, `CASCADE`). */
    @Query("SELECT * FROM loans WHERE accountId = :accountId AND userId = :userId AND deletedAt IS NULL")
    suspend fun getAllForAccount(accountId: Long, userId: Long): List<LoanEntity>

    /** Réservé à `SyncEngineImpl` — voir la KDoc de `CategoryDao.getBySyncId` (même raisonnement,
     * volontairement SANS filtre `deletedAt IS NULL` ni `userId`). */
    @Query("SELECT * FROM loans WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): LoanEntity?

    /** Réservé aux résolveurs `*SyncEnqueuer` — voir la KDoc de `AccountDao.getByIdIncludingDeleted`
     * (même raisonnement, filet de sécurité pour un prêt/emprunt soft-supprimé dans la même cascade
     * qu'un remboursement qui le référence, voir `LoanRepositoryImpl.deleteLoan`). */
    @Query("SELECT * FROM loans WHERE id = :id AND userId = :userId")
    suspend fun getByIdIncludingDeleted(id: Long, userId: Long): LoanEntity?

    /** Réservé à `SyncEngineImpl.enqueueUnsyncedLocalData` — voir la KDoc de
     * `CategoryDao.getUnsyncedForUser` (même raisonnement). */
    @Query("SELECT * FROM loans WHERE userId = :userId AND deletedAt IS NULL AND syncId IS NULL")
    suspend fun getUnsyncedForUser(userId: Long): List<LoanEntity>

    /** Retourne l'id de la ligne insérée, ou -1 en cas de mise à jour (voir `AccountDao.upsert`). */
    @Upsert
    suspend fun upsert(loan: LoanEntity): Long

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert` : toujours une
     * insertion neuve avec `id = 0L` lors d'une restauration, jamais une mise à jour). */
    @Insert
    suspend fun insertAll(loans: List<LoanEntity>): List<Long>

    /** Suppression DOUCE (voir la doc de tête) : `deletedAt`/`updatedAt` seulement, même principe
     * que `AccountDao.softDeleteById`. Ne touche PAS `loan_payments` — voir
     * `LoanRepositoryImpl.deleteLoan`, qui supprime explicitement (suppression DOUCE désormais,
     * étape 19) tout ce qui en dépend AVANT d'appeler cette méthode : la cascade SQLite `loanId` ne
     * se déclenche que sur un vrai `DELETE`, jamais sur cet `UPDATE`. */
    @Query("UPDATE loans SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND userId = :userId")
    suspend fun softDeleteById(id: Long, userId: Long, deletedAt: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de [userId]. */
    @Query("DELETE FROM loans WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)

    /** Retrouve le prêt/emprunt dont le DÉCAISSEMENT correspond à cette transaction (voir la doc de
     * `Loan.transactionId`) — utilisé par `LoanRepositoryImpl.findLoanIdForTransaction` pour
     * empêcher l'édition/suppression directe d'une transaction générée automatiquement (voir
     * `TransactionFormViewModel`, section "Synchronisation avec les transactions"). */
    @Query("SELECT id FROM loans WHERE transactionId = :transactionId AND userId = :userId AND deletedAt IS NULL LIMIT 1")
    suspend fun findIdByTransactionId(transactionId: Long, userId: Long): Long?
}
