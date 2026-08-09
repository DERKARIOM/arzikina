package com.arzikina.ne.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.arzikina.ne.data.local.entity.CardSecretEntity

/**
 * Accès Room à `card_secrets` (voir [CardSecretEntity]). Aucune requête filtrée par `userId` ici
 * (contrairement à [AccountDao]) : l'isolation multi-utilisateurs est déjà garantie par la clé
 * étrangère CASCADE vers `accounts`, elle-même filtrée par `userId` — un `accountId` accessible à
 * l'appelant appartient nécessairement déjà à l'utilisateur courant (voir `AccountRepositoryImpl`).
 */
@Dao
interface CardSecretDao {

    @Query("SELECT * FROM card_secrets WHERE accountId = :accountId")
    suspend fun getByAccountId(accountId: Long): CardSecretEntity?

    @Upsert
    suspend fun upsert(secret: CardSecretEntity)
}
