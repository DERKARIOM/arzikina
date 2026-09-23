package com.naniger.arzikina.data.repository

/**
 * Fournit le token de synchronisation brut et déchiffré, valide (non expiré) — réservé aux
 * collaborateurs de la couche DATA qui en ont un besoin technique direct : le futur intercepteur
 * OkHttp qui posera l'en-tête `Authorization: Bearer ...` sur les appels `api/sync/` (voir
 * `data/remote/api/SyncAuthApi.kt`, qui lui n'en a volontairement pas besoin — c'est justement ce
 * endpoint qui délivre le token).
 *
 * Volontairement ABSENT de [com.naniger.arzikina.domain.repository.SyncAuthRepository] : le token en
 * clair ne doit jamais remonter jusqu'au domaine ou à la présentation (voir la KDoc de
 * [com.naniger.arzikina.domain.model.SyncSession]) — seul ce contrat, interne à la couche data, y
 * donne accès. Implémenté par [TokenProviderImpl], qui partage le même stockage sous-jacent
 * ([SyncAuthStore]) que [SyncAuthRepositoryImpl] — sans dupliquer la logique de lecture/écriture
 * du token (voir `di/RepositoryModule.kt`).
 *
 * Package `data.repository` (pas `data.remote`) : voir docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md,
 * diagnostic Sync Engine — isolation d'un second bug de résolution de types KSP/Dagger, distinct
 * de celui déjà résolu sur `SyncAuthApi` (Retrofit).
 */
interface TokenProvider {
    /** `null` si aucune session active, ou si le token stocké est expiré. */
    suspend fun getValidRawToken(): String?
}
