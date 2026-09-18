package com.arzikina.ne.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Forme exacte d'un modèle de transaction dans le corps de `POST /api/sync/push.php` (voir
 * `server/api/config/entity_sync_configs.php`, entrée `transaction_templates`) — même
 * raisonnement que [RecurringTransactionSyncPayload] (voir sa KDoc pour [id]/[baseVersion]).
 *
 * [accountSyncId]/[categorySyncId] : PAS les `id` Room locaux de
 * [com.arzikina.ne.data.local.entity.TransactionTemplateEntity] — voir la KDoc de tête de
 * `TransactionSyncPayload.kt`. [categorySyncId] TOUJOURS renseigné (contrairement à
 * `RecurringTransactionSyncPayload.categorySyncId`, nullable) : un modèle ne représente jamais un
 * virement entre comptes propres, voir la doc de tête de
 * [com.arzikina.ne.domain.model.TransactionTemplate]. Résolus directement par
 * `TransactionTemplateRepositoryImpl` (pas de classe partagée type `LoanSyncEnqueuer` : un seul
 * propriétaire d'écriture pour cette entité, même raisonnement que `RecurringTransaction`).
 *
 * [isFavorite] : booléen Kotlin, sérialisé `true`/`false` dans CE payload SORTANT — voir
 * `entity_sync_configs.php` (`type: 'int'`, conversion PHP) pour l'écriture. ⚠️ Le sens RETOUR
 * ([TransactionTemplateServerStateDto] ci-dessous) est différent : MySQL/PDO renvoie cette colonne
 * comme un entier JSON (`0`/`1`), jamais `true`/`false` — même raisonnement que
 * `RecurringTransactionServerStateDto.isActive`.
 *
 * [defaultHour]/[defaultMinute] : "Heure par défaut", extension optionnelle du cahier des charges
 * — `null` = pas d'heure par défaut, transporté tel quel (colonnes MySQL nullables, voir
 * `database/migrations/005_add_transaction_templates.sql`).
 */
@Serializable
data class TransactionTemplateSyncPayload(
    val id: String,
    val baseVersion: Int?,
    val name: String,
    val type: String,
    val amount: Long,
    val categorySyncId: String,
    val accountSyncId: String,
    val description: String,
    val isFavorite: Boolean,
    val defaultHour: Int?,
    val defaultMinute: Int?,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Forme exacte de `serverEntity` pour `transaction_templates` — voir la KDoc de
 * [RecurringTransactionServerStateDto] (même raisonnement, `userId` ignoré à la désérialisation).
 * Résolution inverse (→ `id` locaux) par `SyncEngineImpl.applyTransactionTemplateServerState`.
 */
@Serializable
data class TransactionTemplateServerStateDto(
    val id: String,
    val name: String,
    val type: String,
    val amount: Long,
    val categorySyncId: String,
    val accountSyncId: String,
    val description: String,
    /** `0`/`1`, PAS `Boolean` — voir la KDoc de tête de ce fichier : conversion en `Boolean` faite
     * explicitement côté appelant (`SyncEngineImpl.applyTransactionTemplateServerState`). */
    val isFavorite: Int = 0,
    val defaultHour: Int? = null,
    val defaultMinute: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)
