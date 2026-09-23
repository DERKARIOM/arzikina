package com.naniger.arzikina.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Forme exacte d'une personne dans le corps de `POST /api/sync/push.php` (voir
 * `server/api/sync/push.php`, fonction `createPerson`/`upsertExistingPerson`) — même raisonnement
 * que [CategorySyncPayload] (voir sa KDoc pour [id]/[baseVersion]).
 *
 * [phone] : `null` explicite envoyé tel quel (voir `array_key_exists` côté serveur, KDoc de tête de
 * `push.php`) — même raisonnement que [SavingsGoalSyncPayload.deadline]. Les prêts/emprunts
 * associés (`loans`) ne font PAS partie de ce payload : ils ne sont pas synchronisés à cette étape
 * (voir `PersonRepositoryImpl`).
 */
@Serializable
data class PersonSyncPayload(
    val id: String,
    val baseVersion: Int?,
    val name: String,
    val phone: String?,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Forme exacte de `serverEntity` pour `persons` dans la réponse de `push.php`/`pull.php` — voir la
 * KDoc de [CategoryServerStateDto] (même raisonnement, `userId` ignoré à la désérialisation).
 */
@Serializable
data class PersonServerStateDto(
    val id: String,
    val name: String,
    val phone: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)
