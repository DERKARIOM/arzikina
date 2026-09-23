package com.naniger.arzikina.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Forme exacte d'une dépense prévue dans le corps de `POST /api/sync/push.php` (voir
 * `server/api/config/entity_sync_configs.php`, entrée `financial_plan_items`) — même raisonnement
 * que [CategorySyncPayload] (voir sa KDoc pour [id]/[baseVersion]).
 *
 * [planSyncId] : PAS l'`id` Room local de
 * [com.naniger.arzikina.data.local.entity.FinancialPlanItemEntity.planId] (sans aucun sens d'un appareil
 * à l'autre) — voir la KDoc de tête de `TransactionSyncPayload.kt`. JAMAIS nul (une dépense prévue
 * appartient toujours à une planification). [categorySyncId]/[transactionSyncId] : nullables, même
 * raisonnement que `RecurringTransactionSyncPayload.categorySyncId`/`transactionSyncId` (la seconde
 * n'est renseignée qu'après conversion, voir
 * [com.naniger.arzikina.data.local.entity.FinancialPlanItemEntity.transactionId]). Résolus directement par
 * `FinancialPlanRepositoryImpl` (pas de classe partagée type `LoanSyncEnqueuer` : un seul
 * propriétaire d'écriture pour cette entité).
 */
@Serializable
data class FinancialPlanItemSyncPayload(
    val id: String,
    val baseVersion: Int?,
    val planSyncId: String,
    val name: String,
    val amount: Long,
    val actualAmount: Long?,
    val categorySyncId: String?,
    val description: String?,
    val plannedDate: Long?,
    val priority: String,
    val status: String,
    val transactionSyncId: String?,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Forme exacte de `serverEntity` pour `financial_plan_items` — voir la KDoc de
 * [CategoryServerStateDto] (même raisonnement, `userId` ignoré à la désérialisation). Résolution
 * inverse (→ `id` locaux) par `SyncEngineImpl.applyFinancialPlanItemServerState`.
 */
@Serializable
data class FinancialPlanItemServerStateDto(
    val id: String,
    val planSyncId: String,
    val name: String,
    val amount: Long,
    val actualAmount: Long? = null,
    val categorySyncId: String? = null,
    val description: String? = null,
    val plannedDate: Long? = null,
    val priority: String,
    val status: String,
    val transactionSyncId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)
