package com.arzikina.ne.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Forme exacte d'une règle récurrente dans le corps de `POST /api/sync/push.php` (voir
 * `server/api/config/entity_sync_configs.php`, entrée `recurring_transactions`) — même
 * raisonnement que [CategorySyncPayload] (voir sa KDoc pour [id]/[baseVersion]).
 *
 * [accountSyncId]/[categorySyncId] : PAS les `id` Room locaux de
 * [com.arzikina.ne.data.local.entity.RecurringTransactionEntity] (sans aucun sens d'un appareil à
 * l'autre) — voir la KDoc de tête de `TransactionSyncPayload.kt`. Résolus directement par
 * `RecurringTransactionRepositoryImpl` (pas de classe partagée type `LoanSyncEnqueuer` : un seul
 * propriétaire d'écriture pour cette entité, contrairement à `Transaction`/`Loan`).
 *
 * [isActive] : booléen Kotlin, sérialisé `true`/`false` dans CE payload SORTANT — voir
 * `entity_sync_configs.php` (`type: 'int'`, conversion PHP) pour l'écriture. ⚠️ Le sens RETOUR
 * ([RecurringTransactionServerStateDto] ci-dessous) est différent : MySQL/PDO renvoie cette colonne
 * comme un entier JSON (`0`/`1`), jamais `true`/`false` — même raisonnement que
 * `AccountSyncPayload.isExcludedFromStatistics` (voir sa KDoc de tête).
 */
@Serializable
data class RecurringTransactionSyncPayload(
    val id: String,
    val baseVersion: Int?,
    val type: String,
    val amount: Long,
    val accountSyncId: String,
    val categorySyncId: String?,
    val description: String,
    val paymentMethod: String?,
    val startDate: Long,
    val endDate: Long?,
    val frequency: String,
    val nextExecutionDate: Long,
    val isActive: Boolean,
    val triggerHour: Int,
    val triggerMinute: Int,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Forme exacte de `serverEntity` pour `recurring_transactions` — voir la KDoc de
 * [CategoryServerStateDto] (même raisonnement, `userId` ignoré à la désérialisation). Résolution
 * inverse (→ `id` locaux) par `SyncEngineImpl.applyRecurringTransactionServerState`.
 */
@Serializable
data class RecurringTransactionServerStateDto(
    val id: String,
    val type: String,
    val amount: Long,
    val accountSyncId: String,
    val categorySyncId: String? = null,
    val description: String,
    val paymentMethod: String? = null,
    val startDate: Long,
    val endDate: Long? = null,
    val frequency: String,
    val nextExecutionDate: Long,
    /** `0`/`1`, PAS `Boolean` — voir la KDoc de tête de ce fichier : conversion en `Boolean` faite
     * explicitement côté appelant (`SyncEngineImpl.applyRecurringTransactionServerState`). */
    val isActive: Int = 0,
    val triggerHour: Int,
    val triggerMinute: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)

/**
 * Forme exacte d'une occurrence dans le corps de `POST /api/sync/push.php` (voir
 * `server/api/config/entity_sync_configs.php`, entrée `recurring_transaction_occurrences`) — voir
 * la KDoc de tête de [RecurringTransactionSyncPayload] pour le raisonnement complet.
 * [transactionSyncId] : `null` tant que [status] reste `PENDING`/`REJECTED` (voir
 * [com.arzikina.ne.data.local.entity.RecurringTransactionOccurrenceEntity.transactionId]).
 */
@Serializable
data class RecurringTransactionOccurrenceSyncPayload(
    val id: String,
    val baseVersion: Int?,
    val recurringTransactionSyncId: String,
    val scheduledDate: Long,
    val status: String,
    val transactionSyncId: String?,
    val processedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Forme exacte de `serverEntity` pour `recurring_transaction_occurrences` — voir la KDoc de
 * [RecurringTransactionServerStateDto]. Résolution inverse par
 * `SyncEngineImpl.applyRecurringTransactionOccurrenceServerState`.
 */
@Serializable
data class RecurringTransactionOccurrenceServerStateDto(
    val id: String,
    val recurringTransactionSyncId: String,
    val scheduledDate: Long,
    val status: String,
    val transactionSyncId: String? = null,
    val processedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)
