package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/** Voir `data/local/dao/AccountDao` pour le raisonnement sur le filtrage systématique par `userId`. */
@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY date DESC")
    fun observeAllForUser(userId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id AND userId = :userId")
    suspend fun getById(id: Long, userId: Long): TransactionEntity?

    /** Utilisé par `AccountRepositoryImpl.deleteAccount` pour anticiper la cascade SQL sur
     * `accountId` (voir `TransactionEntity`) avant de supprimer le compte. */
    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND userId = :userId")
    suspend fun getAllForAccount(accountId: Long, userId: Long): List<TransactionEntity>

    /** Même besoin que [getAllForAccount], côté `transferAccountId` (compte DESTINATION d'un
     * virement, voir `TransactionEntity.transferAccountId`) — également en `CASCADE`. */
    @Query("SELECT * FROM transactions WHERE transferAccountId = :accountId AND userId = :userId")
    suspend fun getAllForTransferAccount(accountId: Long, userId: Long): List<TransactionEntity>

    /** Neutralise un pointeur `feeTransactionId` devenu mort : voir `AccountRepositoryImpl.deleteAccount`,
     * appelé quand la transaction de frais désignée par [feeTransactionId] est sur le point de
     * disparaître en cascade (compte des frais supprimé) alors que la transaction PARENTE (celle
     * qui porte ce pointeur) survit, potentiellement sur un compte différent. Sans `ForeignKey`
     * sur cette colonne (voir `TransactionEntity`), SQLite ne le ferait jamais lui-même. */
    @Query("UPDATE transactions SET feeTransactionId = NULL WHERE feeTransactionId = :feeTransactionId AND userId = :userId")
    suspend fun clearFeeTransactionReference(feeTransactionId: Long, userId: Long)

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

    @Query("DELETE FROM transactions WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: Long, userId: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de [userId]. */
    @Query("DELETE FROM transactions WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
