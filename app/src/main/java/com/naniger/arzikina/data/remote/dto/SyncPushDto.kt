package com.naniger.arzikina.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Corps de `POST /api/sync/push.php` — voir `server/api/sync/push.php` pour le contrat exact.
 * Reflète directement un lot d'entrées `sync_queue` PARTAGEANT le même `entityType` (voir
 * `SyncEngineImpl`, qui regroupe les entrées avant d'envoyer) : le serveur n'accepte qu'UN seul
 * `entityType` par appel.
 *
 * [SyncPushOperationDto.entity] est un [JsonElement] brut plutôt qu'un type concret
 * (`CategorySyncPayload`...) : `SyncQueueEntity.payloadJson` est déjà sérialisé au moment de
 * l'enfilage (voir `CategoryRepositoryImpl.enqueueCategorySync`) — ce DTO n'a besoin de connaître
 * QUE l'enveloppe (`entityType`/`operations`), jamais la forme interne d'une entité précise,
 * cohérent avec le principe déjà appliqué par `SyncQueueEnqueuer`.
 */
@Serializable
data class SyncPushRequestDto(
    val entityType: String,
    val operations: List<SyncPushOperationDto>
)

@Serializable
data class SyncPushOperationDto(
    val operation: String,
    val entity: JsonElement
)

/**
 * Réponse de `push.php` — `results` préserve l'ORDRE de `operations` envoyé (voir la doc de
 * `push.php`) : `SyncEngineImpl` peut donc associer `results[i]` à l'entrée `sync_queue` qui a
 * produit `operations[i]`, sans avoir besoin d'un identifiant de corrélation supplémentaire.
 */
@Serializable
data class SyncPushResponseDto(
    val results: List<SyncPushResultDto>,
    val serverTime: Long
)

/**
 * [status] : `"accepted"` | `"conflict_resolved"` | `"error"` (voir `push.php`). [serverEntity],
 * présent sauf en cas d'erreur, contient l'état FINAL côté serveur — [SyncEngineImpl] l'applique
 * TEL QUEL sur la ligne locale, y compris pour `"accepted"` (voir la doc de `push.php`, section
 * résolution de conflit : la ligne locale doit toujours refléter `version`/`updatedAt` confirmés
 * par le serveur, jamais rester sur ses propres valeurs optimistes).
 */
@Serializable
data class SyncPushResultDto(
    val status: String,
    val entityId: String? = null,
    val serverEntity: JsonElement? = null,
    val errorCode: String? = null
)
