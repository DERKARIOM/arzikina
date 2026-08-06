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
 */
@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Upsert
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Utilisé uniquement par la restauration d'une sauvegarde (remplacement complet). */
    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}
