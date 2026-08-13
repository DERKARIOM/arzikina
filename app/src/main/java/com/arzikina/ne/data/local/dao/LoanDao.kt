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
 */
@Dao
interface LoanDao {

    @Query("SELECT * FROM loans WHERE userId = :userId ORDER BY dueDate ASC")
    fun observeAllForUser(userId: Long): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE id = :id AND userId = :userId")
    suspend fun getById(id: Long, userId: Long): LoanEntity?

    /** Lecture ponctuelle (hors `Flow`) : utilisée par `PersonRepositoryImpl.deletePerson` pour
     * nettoyer les transactions liées AVANT de supprimer la personne (voir `PersonDao.deleteById`). */
    @Query("SELECT * FROM loans WHERE personId = :personId AND userId = :userId")
    suspend fun getAllForPerson(personId: Long, userId: Long): List<LoanEntity>

    /** Lecture ponctuelle (hors `Flow`) : utilisée par `AccountRepositoryImpl.deleteAccount` pour
     * nettoyer les transactions liées (décaissement + remboursements, même sur un autre compte)
     * AVANT que la suppression du compte ne cascade sur ces prêts/emprunts (voir
     * `LoanEntity.accountId`, `CASCADE`). */
    @Query("SELECT * FROM loans WHERE accountId = :accountId AND userId = :userId")
    suspend fun getAllForAccount(accountId: Long, userId: Long): List<LoanEntity>

    /** Retourne l'id de la ligne insérée, ou -1 en cas de mise à jour (voir `AccountDao.upsert`). */
    @Upsert
    suspend fun upsert(loan: LoanEntity): Long

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert` : toujours une
     * insertion neuve avec `id = 0L` lors d'une restauration, jamais une mise à jour). */
    @Insert
    suspend fun insertAll(loans: List<LoanEntity>): List<Long>

    /** Supprime aussi, en cascade SQLite, tous les remboursements de ce prêt/emprunt (voir
     * `LoanPaymentEntity`). Les transactions liées (décaissement + remboursements) ne sont PAS
     * supprimées automatiquement : c'est la responsabilité de `LoanRepositoryImpl.deleteLoan`. */
    @Query("DELETE FROM loans WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: Long, userId: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de [userId]. */
    @Query("DELETE FROM loans WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)

    /** Retrouve le prêt/emprunt dont le DÉCAISSEMENT correspond à cette transaction (voir la doc de
     * `Loan.transactionId`) — utilisé par `LoanRepositoryImpl.findLoanIdForTransaction` pour
     * empêcher l'édition/suppression directe d'une transaction générée automatiquement (voir
     * `TransactionFormViewModel`, section "Synchronisation avec les transactions"). */
    @Query("SELECT id FROM loans WHERE transactionId = :transactionId AND userId = :userId LIMIT 1")
    suspend fun findIdByTransactionId(transactionId: Long, userId: Long): Long?
}
