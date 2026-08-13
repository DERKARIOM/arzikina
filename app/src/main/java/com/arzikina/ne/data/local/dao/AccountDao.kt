package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

/**
 * Accès Room aux comptes.
 *
 * Les écritures passent par [Upsert] : la couche appelante fournit l'état
 * final souhaité sans avoir à savoir si le compte existe déjà en base.
 *
 * Toutes les requêtes paramétrées par un `id` filtrent aussi par `userId` :
 * même une clé primaire connue ne doit jamais permettre de lire/modifier la
 * ligne d'un autre utilisateur (voir `data/repository/AccountRepositoryImpl`).
 */
@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE userId = :userId ORDER BY createdAt ASC")
    fun observeAllForUser(userId: Long): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id AND userId = :userId")
    suspend fun getById(id: Long, userId: Long): AccountEntity?

    /** Retourne l'id de la ligne insérée, ou -1 en cas de mise à jour (comportement standard
     * de Room @Upsert) — voir `AccountRepositoryImpl.saveAccount` pour la logique qui en dépend. */
    @Upsert
    suspend fun upsert(account: AccountEntity): Long

    /** Utilisé UNIQUEMENT par la restauration d'une sauvegarde (`BackupRepositoryImpl`), qui force
     * toujours `id = 0L` avant d'appeler cette méthode : `@Insert` (pas `@Upsert`) car c'est
     * TOUJOURS une insertion neuve, jamais une mise à jour d'une ligne existante — voir la doc de
     * tête de `BackupMappers` sur la réattribution des ids. Retourne les ids générés, dans le même
     * ordre que [accounts], pour construire la table de correspondance ancien → nouvel id. */
    @Insert
    suspend fun insertAll(accounts: List<AccountEntity>): List<Long>

    @Query("DELETE FROM accounts WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: Long, userId: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de [userId]. */
    @Query("DELETE FROM accounts WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
