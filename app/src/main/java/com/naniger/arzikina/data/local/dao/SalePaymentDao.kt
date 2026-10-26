package com.naniger.arzikina.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
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
    /** Lecture par id, y compris une ligne supprimée (résolution de références pour la synchronisation). */
    @Query("SELECT * FROM sale_payments WHERE id = :id AND userId = :userId")
    suspend fun getById(id: Long, userId: Long): SalePaymentEntity?

    /** Réservé au gestionnaire de synchronisation (voir `ShopSyncHandler`) : `syncId` est unique tous utilisateurs confondus. */
    @Query("SELECT * FROM sale_payments WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): SalePaymentEntity?

    /** Insère (id 0) ou met à jour la ligne telle que reçue du serveur. */
    @Upsert
    suspend fun upsert(row: SalePaymentEntity): Long

    /** Rattrapage de la synchronisation : lignes jamais enfilées (voir `ShopSyncHandler.enqueueUnsynced`). */
    @Query("SELECT * FROM sale_payments WHERE userId = :userId AND deletedAt IS NULL AND syncId IS NULL ORDER BY id")
    suspend fun getUnsyncedForUser(userId: Long): List<SalePaymentEntity>

    /** Attribue l'identité de synchronisation (voir `ShopSyncEnqueuer`) ; sans effet sur `updatedAt`. */
    @Query("UPDATE sale_payments SET syncId = :syncId WHERE id = :id AND userId = :userId")
    suspend fun setSyncId(id: Long, userId: Long, syncId: String)

    @Query("DELETE FROM sale_payments WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
