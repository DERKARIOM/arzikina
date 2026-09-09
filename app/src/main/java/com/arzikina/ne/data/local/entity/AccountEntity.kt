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
    indices = [Index("userId"), Index(value = ["syncId"], unique = true)]
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
    val isExcludedFromStatistics: Boolean = false,
    /** Voir [com.arzikina.ne.domain.model.Account.mobileMoneyPackageName] — ajouté en v17 (voir
     * `MIGRATION_16_17`). `null` par défaut : un compte déjà existant n'a simplement aucune
     * application associée, exactement comme avant l'introduction de ce champ. */
    val mobileMoneyPackageName: String? = null,
    /** Voir [com.arzikina.ne.domain.model.Account.displayOrder] — ajouté en v28 (voir
     * `MIGRATION_27_28`), qui rattrape aussi les lignes déjà existantes (comptage par utilisateur,
     * ordonné par `createdAt`) pour préserver l'ordre affiché avant l'introduction de ce champ. */
    val displayOrder: Long = 0L,
    /** UUID partagé Android/API/MySQL pour la synchronisation multi-appareils — additif, voir
     * `docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md` (section 6.3, option B) : l'[id] Room local
     * ci-dessus reste la clé primaire, inchangée. `null` tant que cette ligne n'a jamais été
     * envoyée au serveur (généré par le futur Sync Engine, pas à la création locale — schéma seul
     * pour l'instant, rien ne l'alimente encore). */
    val syncId: String? = null,
    /** Horodatage de dernière modification, pour la détection de conflit lors de la
     * synchronisation (Last-Write-Wins, voir section 9 du document ci-dessus). `0L` par défaut
     * (rattrapé à [createdAt] pour les lignes déjà existantes par `MIGRATION_22_23`) tant que le
     * Sync Engine ne renseigne pas encore ce champ à chaque écriture. */
    val updatedAt: Long = 0L,
    /** Suppression douce, voir section 8 du document ci-dessus : `null` = ligne active. La
     * suppression reste un `DELETE` SQL immédiat tant que le Sync Engine n'est pas branché dans les
     * repositories (étape ultérieure) — ce champ n'est pour l'instant jamais renseigné. */
    val deletedAt: Long? = null,
    /** Compteur de version optimiste, pour la détection de conflit côté serveur (section 9). */
    val version: Int = 1
)
