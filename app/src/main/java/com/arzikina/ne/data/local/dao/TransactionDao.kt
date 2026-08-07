package com.arzikina.ne.data.local.dao

import androidx.room.Dao
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

    @Upsert
    suspend fun upsert(transaction: TransactionEntity)

    /** Utilisé uniquement par la restauration d'une sauvegarde (remplacement complet). */
    @Upsert
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: Long, userId: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de [userId]. */
    @Query("DELETE FROM transactions WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
