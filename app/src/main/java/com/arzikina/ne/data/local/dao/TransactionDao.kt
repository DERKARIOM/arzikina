package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Voir `data/local/dao/AccountDao` pour le raisonnement sur le filtrage systématique par `userId`.
 *
 * Suppression DOUCE (voir `CategoryDao`/`AccountDao` pour le raisonnement complet, même principe) :
 * toutes les lectures ci-dessous filtrent `deletedAt IS NULL`, `deleteById` a été retiré (étape
 * 19.5, dernier appelant migré vers [softDeleteById] depuis la fin de l'étape 17.3) — même principe
 * que `AccountDao`/`PersonDao`.
 */
@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE userId = :userId AND deletedAt IS NULL ORDER BY date DESC")
    fun observeAllForUser(userId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id AND userId = :userId AND deletedAt IS NULL")
    suspend fun getById(id: Long, userId: Long): TransactionEntity?

    /** Réservé à `SyncEngineImpl` — voir la KDoc de `CategoryDao.getBySyncId` (même raisonnement,
     * volontairement SANS filtre `deletedAt IS NULL` ni `userId`). */
    @Query("SELECT * FROM transactions WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): TransactionEntity?

    /** Réservé aux résolveurs `*SyncEnqueuer` — voir la KDoc de `AccountDao.getByIdIncludingDeleted`
     * (même raisonnement ; utilisé ici pour résoudre `feeTransactionSyncId` même quand la
     * transaction de frais a déjà été soft-supprimée dans la même cascade). */
    @Query("SELECT * FROM transactions WHERE id = :id AND userId = :userId")
    suspend fun getByIdIncludingDeleted(id: Long, userId: Long): TransactionEntity?

    /** Réservé à `SyncEngineImpl.enqueueUnsyncedLocalData` — voir la KDoc de
     * `CategoryDao.getUnsyncedForUser` (même raisonnement). */
    @Query("SELECT * FROM transactions WHERE userId = :userId AND deletedAt IS NULL AND syncId IS NULL")
    suspend fun getUnsyncedForUser(userId: Long): List<TransactionEntity>

    /** Utilisé par `AccountRepositoryImpl.deleteAccount` pour anticiper la cascade SQL sur
     * `accountId` (voir `TransactionEntity`) avant de supprimer le compte. */
    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND userId = :userId AND deletedAt IS NULL")
    suspend fun getAllForAccount(accountId: Long, userId: Long): List<TransactionEntity>

    /** Même besoin que [getAllForAccount], côté `transferAccountId` (compte DESTINATION d'un
     * virement, voir `TransactionEntity.transferAccountId`) — également en `CASCADE`. */
    @Query("SELECT * FROM transactions WHERE transferAccountId = :accountId AND userId = :userId AND deletedAt IS NULL")
    suspend fun getAllForTransferAccount(accountId: Long, userId: Long): List<TransactionEntity>

    /** Neutralise un pointeur `feeTransactionId` devenu mort : voir `AccountRepositoryImpl.deleteAccount`,
     * appelé quand la transaction de frais désignée par [feeTransactionId] est sur le point de
     * disparaître en cascade (compte des frais supprimé) alors que la transaction PARENTE (celle
     * qui porte ce pointeur) survit, potentiellement sur un compte différent. Sans `ForeignKey`
     * sur cette colonne (voir `TransactionEntity`), SQLite ne le ferait jamais lui-même. */
    @Query("UPDATE transactions SET feeTransactionId = NULL WHERE feeTransactionId = :feeTransactionId AND userId = :userId")
    suspend fun clearFeeTransactionReference(feeTransactionId: Long, userId: Long)

    /** Voir `TransactionEntity.receiptId` — utilisé pour l'anti-doublon du bouton "Ajouter comme
     * transaction" (cahier des charges "Créer une transaction depuis un reçu") : `null` si ce reçu
     * n'a encore donné lieu à aucune transaction. Au plus une ligne possible en pratique (voir
     * `TransactionRepositoryImpl.saveTransaction`, qui empêche la création d'une deuxième
     * transaction pour un même reçu), mais rien ne l'impose au niveau SQL (pas de contrainte
     * `UNIQUE`) — `getById`-style lecture ponctuelle plutôt qu'un flux, cet appel n'a lieu qu'au
     * moment du clic sur le bouton, jamais en continu. */
    @Query("SELECT * FROM transactions WHERE receiptId = :receiptId AND userId = :userId AND deletedAt IS NULL LIMIT 1")
    suspend fun findByReceiptId(receiptId: Long, userId: Long): TransactionEntity?

    /** Neutralise un pointeur `receiptId` devenu mort — même principe que
     * [clearFeeTransactionReference], appelé par `ReceiptRepositoryImpl.deleteReceipt` AVANT de
     * supprimer le reçu (voir `TransactionEntity`, pas de `ForeignKey` vers `receipts`). */
    @Query("UPDATE transactions SET receiptId = NULL WHERE receiptId = :receiptId AND userId = :userId")
    suspend fun clearReceiptReference(receiptId: Long, userId: Long)

    /** Voir `TransactionRepository.observeReceiptIdsWithTransaction` — UNE SEULE requête groupée
     * (`DISTINCT`) pour connaître tous les reçus déjà liés à une transaction, plutôt qu'un appel
     * [findByReceiptId] par reçu affiché dans "Gestion des reçus" (non-N+1, voir
     * `ReceiptsViewModel`/`ReceiptsAdapter`, statut visuel du reçu). */
    @Query("SELECT DISTINCT receiptId FROM transactions WHERE userId = :userId AND receiptId IS NOT NULL AND deletedAt IS NULL")
    fun observeReceiptIdsWithTransaction(userId: Long): Flow<List<Long>>

    /** Retourne l'id de la ligne insérée, ou -1 en cas de mise à jour (voir `AccountDao.upsert`) —
     * nécessaire pour lier une transaction générée automatiquement à un `Loan`/`LoanPayment`
     * (voir `data/repository/LoanRepositoryImpl`). */
    @Upsert
    suspend fun upsert(transaction: TransactionEntity): Long

    /** Voir `AccountDao.insertAll` pour le raisonnement (`@Insert`, jamais `@Upsert` : toujours une
     * insertion neuve avec `id = 0L` lors d'une restauration, jamais une mise à jour). Utilisé en
     * DEUX passes par `BackupRepositoryImpl` pour la transaction de frais auto-référencée (voir
     * `feeTransactionId`) : cette première passe insère avec `feeTransactionId = null`, une
     * deuxième passe (`upsert` ligne à ligne) réécrit ensuite ce pointeur une fois tous les
     * nouveaux ids connus. */
    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    /** Suppression DOUCE (voir la doc de tête) : `deletedAt`/`updatedAt` seulement, même principe
     * que `CategoryDao.softDeleteById`/`AccountDao.softDeleteById`. */
    @Query("UPDATE transactions SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND userId = :userId")
    suspend fun softDeleteById(id: Long, userId: Long, deletedAt: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de [userId]. */
    @Query("DELETE FROM transactions WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
