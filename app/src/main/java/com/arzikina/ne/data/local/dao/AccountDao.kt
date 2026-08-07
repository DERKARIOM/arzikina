package com.arzikina.ne.data.local.dao

import androidx.room.Dao
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

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Upsert
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Query("DELETE FROM accounts WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: Long, userId: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde : ne purge QUE les données de [userId]. */
    @Query("DELETE FROM accounts WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
