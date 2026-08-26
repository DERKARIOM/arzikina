package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.UserPreferencesEntity
import kotlinx.coroutines.flow.Flow

/**
 * Voir `data/local/dao/AccountDao` pour le raisonnement sur le filtrage systématique par `userId`.
 *
 * Volontairement PLUS PETIT que les autres DAO synchronisés (pas de `softDeleteById`/
 * `insertAll`/`deleteAllForUser`) : voir la KDoc de tête de `UserPreferencesEntity` — au plus une
 * ligne par utilisateur, jamais supprimée ni restaurée depuis une sauvegarde (les préférences
 * d'affichage ne font pas partie du format de sauvegarde actuel).
 */
@Dao
interface UserPreferencesDao {

    @Query("SELECT * FROM user_preferences WHERE userId = :userId AND deletedAt IS NULL LIMIT 1")
    fun observeForUser(userId: Long): Flow<UserPreferencesEntity?>

    @Query("SELECT * FROM user_preferences WHERE userId = :userId AND deletedAt IS NULL LIMIT 1")
    suspend fun getForUser(userId: Long): UserPreferencesEntity?

    /** Réservé à `SyncEngineImpl` — voir la KDoc de `CategoryDao.getBySyncId` (même raisonnement,
     * volontairement SANS filtre `deletedAt IS NULL` ni `userId`). */
    @Query("SELECT * FROM user_preferences WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): UserPreferencesEntity?

    /** Réservé à `SyncEngineImpl.enqueueUnsyncedLocalData` — voir la KDoc de
     * `CategoryDao.getUnsyncedForUser` (même raisonnement). */
    @Query("SELECT * FROM user_preferences WHERE userId = :userId AND deletedAt IS NULL AND syncId IS NULL")
    suspend fun getUnsyncedForUser(userId: Long): List<UserPreferencesEntity>

    /** Retourne l'id de la ligne insérée, ou -1 en cas de mise à jour (voir `AccountDao.upsert`). */
    @Upsert
    suspend fun upsert(preferences: UserPreferencesEntity): Long
}
