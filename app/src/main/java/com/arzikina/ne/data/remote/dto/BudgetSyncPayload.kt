package com.arzikina.ne.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Forme exacte d'un budget dans le corps de `POST /api/sync/push.php` (voir
 * `server/api/config/entity_sync_configs.php`, entrée `budgets`) — même raisonnement que
 * [CategorySyncPayload] (voir sa KDoc pour [id]/[baseVersion]).
 *
 * [categorySyncId] : PAS [com.arzikina.ne.data.local.entity.BudgetEntity.categoryId] (un `id` Room
 * local, sans aucun sens d'un appareil à l'autre) — voir la KDoc de tête de
 * `TransactionSyncPayload.kt` pour le raisonnement complet sur cette classe de champs. Résolu par
 * `BudgetRepositoryImpl` avant l'enfilage (pas de classe partagée type `TransactionSyncEnqueuer`
 * ici : `Budget` n'a qu'UN SEUL propriétaire d'écriture, contrairement à `Transaction`).
 *
 * [startDate]/[endDate] : `null` explicite envoyé tel quel (voir `array_key_exists` côté serveur,
 * KDoc de tête de `push.php`) — même raisonnement que [SavingsGoalSyncPayload.deadline] (budget
 * récurrent, voir la KDoc de [com.arzikina.ne.domain.model.Budget]).
 */
@Serializable
data class BudgetSyncPayload(
    val id: String,
    val baseVersion: Int?,
    val categorySyncId: String,
    val period: String,
    val limitAmount: Long,
    val currencyCode: String,
    val startDate: Long?,
    val endDate: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Forme exacte de `serverEntity` pour `budgets` dans la réponse de `push.php`/`pull.php` — voir la
 * KDoc de [CategoryServerStateDto] (même raisonnement, `userId` ignoré à la désérialisation).
 * [categorySyncId] résolu en sens inverse (→ `categoryId` local) par
 * `SyncEngineImpl.applyBudgetServerState`, même principe que
 * `SyncEngineImpl.applyTransactionServerState`.
 */
@Serializable
data class BudgetServerStateDto(
    val id: String,
    val categorySyncId: String,
    val period: String,
    val limitAmount: Long,
    val currencyCode: String,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)
