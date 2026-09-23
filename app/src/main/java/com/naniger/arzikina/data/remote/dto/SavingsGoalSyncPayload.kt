package com.naniger.arzikina.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Forme exacte d'un objectif d'épargne dans le corps de `POST /api/sync/push.php` (voir
 * `server/api/sync/push.php`, fonction `createSavingsGoal`/`upsertExistingSavingsGoal`) — même
 * raisonnement que [CategorySyncPayload] (voir sa KDoc pour [id]/[baseVersion]).
 *
 * [deadline] : `null` explicite envoyé tel quel (voir `array_key_exists` côté serveur, KDoc de
 * tête de `push.php` sur ce point précis) — distinct d'un champ absent, qu'un DTO Kotlin ne peut de
 * toute façon pas produire ici (propriété non-nullable au sens "toujours sérialisée").
 */
@Serializable
data class SavingsGoalSyncPayload(
    val id: String,
    val baseVersion: Int?,
    val name: String,
    val targetAmount: Long,
    val currentAmount: Long,
    val currencyCode: String,
    val deadline: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Forme exacte de `serverEntity` pour `savings_goals` dans la réponse de `push.php`/`pull.php` —
 * voir la KDoc de [CategoryServerStateDto] (même raisonnement, `userId` ignoré à la
 * désérialisation).
 */
@Serializable
data class SavingsGoalServerStateDto(
    val id: String,
    val name: String,
    val targetAmount: Long,
    val currentAmount: Long,
    val currencyCode: String,
    val deadline: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)
