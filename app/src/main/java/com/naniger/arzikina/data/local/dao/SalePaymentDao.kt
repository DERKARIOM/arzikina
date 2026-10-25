package com.naniger.arzikina.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.naniger.arzikina.data.local.entity.SalePaymentEntity
import kotlinx.coroutines.flow.Flow

/** Accès à la table `sale_payments`. Filtrage systématique par `userId` (voir `AccountDao`). */
@Dao
interface SalePaymentDao {

    @Insert
    suspend fun insert(payment: SalePaymentEntity): Long

    @Query("SELECT * FROM sale_payments WHERE userId = :userId AND saleId = :saleId AND deletedAt IS NULL ORDER BY id")
    fun observeForSale(userId: Long, saleId: Long): Flow<List<SalePaymentEntity>>

    @Query("SELECT * FROM sale_payments WHERE userId = :userId AND saleId = :saleId AND deletedAt IS NULL ORDER BY id")
    suspend fun getForSale(userId: Long, saleId: Long): List<SalePaymentEntity>

    /** Relie l'encaissement à la transaction inverse créée par l'annulation de la vente. */
    @Query("UPDATE sale_payments SET reversalTransactionId = :transactionId, updatedAt = :now WHERE id = :id AND userId = :userId")
    suspend fun setReversal(id: Long, userId: Long, transactionId: Long, now: Long)

    /** Purge TOUTES les lignes de [userId] (restauration de sauvegarde, suppression du compte). */
    @Query("DELETE FROM sale_payments WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
