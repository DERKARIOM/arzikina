package com.arzikina.ne.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Forme exacte d'une planification dans le corps de `POST /api/sync/push.php` (voir
 * `server/api/sync/push.php`, fonction `createFinancialPlan`/`upsertExistingFinancialPlan`) — même
 * raisonnement que [CategorySyncPayload] (voir sa KDoc pour [id]/[baseVersion]).
 *
 * [description]/[targetAmount]/[startDate]/[endDate] : QUATRE champs nullables, `null` explicite
 * envoyé tel quel (voir `array_key_exists` côté serveur, KDoc de tête de `push.php`) — même
 * raisonnement que [SavingsGoalSyncPayload.deadline]. Les dépenses prévues associées
 * (`financial_plan_items`) ne font PAS partie de ce payload : elles ne sont pas synchronisées à
 * cette étape (voir `FinancialPlanRepositoryImpl`).
 */
@Serializable
data class FinancialPlanSyncPayload(
    val id: String,
    val baseVersion: Int?,
    val name: String,
    val description: String?,
    val availableAmount: Long,
    val targetAmount: Long?,
    val periodType: String,
    val startDate: Long?,
    val endDate: Long?,
    val icon: String,
    val colorArgb: Long,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Forme exacte de `serverEntity` pour `financial_plans` dans la réponse de `push.php`/`pull.php` —
 * voir la KDoc de [CategoryServerStateDto] (même raisonnement, `userId` ignoré à la
 * désérialisation).
 */
@Serializable
data class FinancialPlanServerStateDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val availableAmount: Long,
    val targetAmount: Long? = null,
    val periodType: String,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val icon: String,
    val colorArgb: Long,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)
