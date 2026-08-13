package com.arzikina.ne.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arzikina.ne.domain.model.AccountIcon
import com.arzikina.ne.domain.model.AccountType

/**
 * Représentation Room d'un compte. Reste dans la couche data : le domaine
 * manipule uniquement [com.arzikina.ne.domain.model.Account] (voir
 * `data/mapper/AccountMapper`), qui ne connaît PAS [userId] — voir
 * `data/repository/AccountRepositoryImpl` pour le raisonnement (isolation
 * multi-utilisateurs gérée entièrement dans la couche data).
 *
 * Pas de contrainte SQL `FOREIGN KEY` vers `users` (voir
 * [com.arzikina.ne.data.local.database.MIGRATION_6_7] : SQLite ne permet pas
 * d'ajouter une clé étrangère à une table existante sans la recréer
 * entièrement). Un index simple suffit pour les performances de requête ;
 * l'intégrité référentielle (ex. purge des données à la suppression d'un
 * compte utilisateur — fonctionnalité non encore implémentée) sera assurée
 * au niveau applicatif le jour où elle sera nécessaire.
 */
@Entity(
    tableName = "accounts",
    indices = [Index("userId")]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val name: String,
    val icon: AccountIcon,
    val colorArgb: Long,
    val currencyCode: String,
    val initialBalanceMinor: Long,
    val createdAt: Long,
    /** Voir [com.arzikina.ne.domain.model.Account.type] — ajouté en v11 (voir
     * `MIGRATION_10_11`), dérivé de [icon] pour les comptes déjà existants. */
    val type: AccountType = AccountType.CASH,
    /** Voir [com.arzikina.ne.domain.model.Account.cardLastFourDigits]. */
    val cardLastFourDigits: String? = null,
    /** Voir [com.arzikina.ne.domain.model.Account.cardExpiryMonth]. */
    val cardExpiryMonth: Int? = null,
    /** Voir [com.arzikina.ne.domain.model.Account.cardExpiryYear]. */
    val cardExpiryYear: Int? = null,
    /** Voir [com.arzikina.ne.domain.model.Account.isExcludedFromStatistics] — ajouté en v15 (voir
     * `MIGRATION_14_15`). `false` par défaut : un compte existant reste inclus dans les
     * statistiques exactement comme avant l'introduction de ce champ. */
    val isExcludedFromStatistics: Boolean = false
)
