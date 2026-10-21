package com.naniger.arzikina.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.naniger.arzikina.data.local.entity.IssuerProfileEntity
import kotlinx.coroutines.flow.Flow

/** Voir `UserPreferencesDao` : au plus une ligne par utilisateur. */
@Dao
interface IssuerProfileDao {

    @Query("SELECT * FROM issuer_profiles WHERE userId = :userId AND deletedAt IS NULL LIMIT 1")
    fun observeForUser(userId: Long): Flow<IssuerProfileEntity?>

    @Query("SELECT * FROM issuer_profiles WHERE userId = :userId AND deletedAt IS NULL LIMIT 1")
    suspend fun getForUser(userId: Long): IssuerProfileEntity?

    /** Ligne de [userId], supprimée ou non (index unique sur `userId`) — voir `IssuedReceiptSyncHandler`. */
    @Query("SELECT * FROM issuer_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getAnyForUser(userId: Long): IssuerProfileEntity?

    @Query("SELECT * FROM issuer_profiles WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): IssuerProfileEntity?

    @Upsert
    suspend fun upsert(profile: IssuerProfileEntity): Long

    @Query("DELETE FROM issuer_profiles WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
