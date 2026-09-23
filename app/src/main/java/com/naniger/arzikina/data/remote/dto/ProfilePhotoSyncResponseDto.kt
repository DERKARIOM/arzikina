package com.naniger.arzikina.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Réponse de `POST /api/profile/upload_photo.php`, `POST /api/profile/delete_photo.php` ET
 * `GET /api/profile/get.php` — MÊME forme pour les trois (voir la KDoc de tête de chacun côté
 * serveur), déjà en camelCase (comme [LoginResponseDto], sans passer par `toCamelCaseRow()`).
 *
 * [photoPath] : chemin SERVEUR relatif (jamais une URL absolue) — `null` si l'utilisateur n'a pas
 * de photo. Chaque appareil préfixe lui-même sa propre [com.naniger.arzikina.data.remote.RemoteConfig.BASE_URL]
 * pour obtenir une URL téléchargeable (voir [com.naniger.arzikina.data.remote.api.ProfilePhotoApi.downloadPhoto]).
 *
 * [version]/[updatedAt] réutilisent directement `users.version`/`users.updated_at` côté serveur
 * (voir la KDoc de tête de `upload_photo.php`) — base du versionnement/de l'invalidation de cache
 * ET de la résolution de conflit multi-appareils (voir
 * [com.naniger.arzikina.data.repository.ProfilePhotoRepositoryImpl.syncWithServer]).
 */
@Serializable
data class ProfilePhotoSyncResponseDto(
    val photoPath: String?,
    val version: Long,
    val updatedAt: Long
)
