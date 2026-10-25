package com.naniger.arzikina.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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
    @Query("DELETE FROM shops WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
