package com.naniger.arzikina.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Forme exacte des préférences d'affichage dans le corps de `POST /api/sync/push.php` (voir
 * `server/api/config/entity_sync_configs.php`, entrée `user_preferences`) — même raisonnement que
 * [CategorySyncPayload] (voir sa KDoc pour [id]/[baseVersion]).
 *
 * DIFFÉRENT de tous les autres payloads de ce projet : AUCUNE référence croisée (pas de champ
 * `*SyncId`) — voir la KDoc de tête de l'entrée `user_preferences` dans `entity_sync_configs.php`.
 * `biometricLockEnabled` ([com.naniger.arzikina.domain.model.UserPreferences.biometricLockEnabled])
 * volontairement ABSENT : réglage PAR APPAREIL, jamais synchronisé (voir
 * `docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md`, section 6.5).
 */
@Serializable
data class UserPreferencesSyncPayload(
    val id: String,
    val baseVersion: Int?,
    val themeMode: String,
    val currencyCode: String,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Forme exacte de `serverEntity` pour `user_preferences` — voir la KDoc de
 * [CategoryServerStateDto] (même raisonnement, `userId` ignoré à la désérialisation). Résolution
 * (triviale ici, aucune référence croisée) par `SyncEngineImpl.applyUserPreferencesServerState`.
 */
@Serializable
data class UserPreferencesServerStateDto(
    val id: String,
    val themeMode: String,
    val currencyCode: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)
