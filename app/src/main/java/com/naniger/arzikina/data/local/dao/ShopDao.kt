package com.naniger.arzikina.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.naniger.arzikina.data.local.entity.ShopEntity
import kotlinx.coroutines.flow.Flow

/**
 * Accès à la table `shops`. Filtrage systématique par `userId` (voir `AccountDao`) ; V1 : la boutique
 * active est la première non supprimée de l'utilisateur.
 */
@Dao
interface ShopDao {

    @Query("SELECT * FROM shops WHERE userId = :userId AND deletedAt IS NULL ORDER BY id LIMIT 1")
    fun observeFirstForUser(userId: Long): Flow<ShopEntity?>

    @Query("SELECT * FROM shops WHERE userId = :userId AND deletedAt IS NULL ORDER BY id LIMIT 1")
    suspend fun getFirstForUser(userId: Long): ShopEntity?

    /** Mémorise le compte d'encaissement choisi (proposé par défaut aux ventes suivantes). */
    @Query("UPDATE shops SET accountId = :accountId, updatedAt = :now WHERE id = :id AND userId = :userId")
    suspend fun updateAccount(id: Long, userId: Long, accountId: Long, now: Long)

    /** Retourne l'id de la ligne insérée. */
    @Insert
    suspend fun insert(shop: ShopEntity): Long

    /** Purge TOUTES les lignes de [userId] (restauration de sauvegarde, suppression du compte). */
    /** Lecture par id, y compris une ligne supprimée (résolution de références pour la synchronisation). */
    @Query("SELECT * FROM shops WHERE id = :id AND userId = :userId")
    suspend fun getById(id: Long, userId: Long): ShopEntity?

    /** Réservé au gestionnaire de synchronisation (voir `ShopSyncHandler`) : `syncId` est unique tous utilisateurs confondus. */
    @Query("SELECT * FROM shops WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): ShopEntity?

    /** Insère (id 0) ou met à jour la ligne telle que reçue du serveur. */
    @Upsert
    suspend fun upsert(row: ShopEntity): Long

    /** Rattrapage de la synchronisation : lignes jamais enfilées (voir `ShopSyncHandler.enqueueUnsynced`). */
    @Query("SELECT * FROM shops WHERE userId = :userId AND deletedAt IS NULL AND syncId IS NULL ORDER BY id")
    suspend fun getUnsyncedForUser(userId: Long): List<ShopEntity>

    /** Attribue l'identité de synchronisation (voir `ShopSyncEnqueuer`) ; sans effet sur `updatedAt`. */
    @Query("UPDATE shops SET syncId = :syncId WHERE id = :id AND userId = :userId")
    suspend fun setSyncId(id: Long, userId: Long, syncId: String)

    @Query("DELETE FROM shops WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
