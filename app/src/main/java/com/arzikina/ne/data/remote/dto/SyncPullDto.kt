package com.arzikina.ne.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Réponse de `GET /api/sync/pull.php` — voir sa doc de tête pour le contrat exact (pull incrémental
 * via `updated_after`, lot plafonné à 500 lignes). [entities] est une liste de [JsonElement] bruts
 * (même raisonnement que [SyncPushOperationDto.entity]) : cette enveloppe n'a pas besoin de
 * connaître la forme interne d'une entité précise — voir `SyncEngineImpl.pullRemoteChanges`, qui
 * décode chaque élément en `CategoryServerStateDto` (même DTO que celui appliqué après un push,
 * voir sa KDoc — c'est exactement la même forme de ligne des deux côtés).
 *
 * [serverTime] doit être conservé comme prochain `updated_after` (voir [SyncCursorStore][com.arzikina.ne.data.repository.SyncCursorStore])
 * — jamais l'horloge de l'appareil, pour éviter tout décalage.
 */
@Serializable
data class SyncPullResponseDto(
    val entities: List<JsonElement>,
    val serverTime: Long
)
