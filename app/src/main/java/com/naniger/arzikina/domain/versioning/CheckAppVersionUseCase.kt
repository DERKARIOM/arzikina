package com.naniger.arzikina.domain.versioning

import javax.inject.Inject

/**
 * Évalue si l'app peut être utilisée. Deux temps, pour ne jamais faire attendre l'utilisateur :
 * - [fromCache] : instantané, hors réseau — applique immédiatement un blocage déjà connu, même
 *   hors ligne (un utilisateur ne peut pas contourner l'obligation en coupant le réseau) ;
 * - [refresh] : interroge le serveur ; en cas d'échec réseau, se rabat sur le cache ; sans aucune
 *   donnée, laisse passer (voir [VersionGate]).
 */
class CheckAppVersionUseCase @Inject constructor(
    private val repository: VersionPolicyRepository,
    private val installedVersionProvider: InstalledVersionProvider
) {

    suspend fun fromCache(): VersionGateResult = VersionGate.evaluate(
        installed = installedVersionProvider.installedVersion(),
        policy = repository.cachedPolicy(),
        includeMaintenance = false
    )

    suspend fun refresh(): VersionGateResult {
        val installed = installedVersionProvider.installedVersion()
        val fresh = repository.fetchPolicy()
        return if (fresh != null) {
            VersionGate.evaluate(installed, fresh, includeMaintenance = true)
        } else {
            VersionGate.evaluate(installed, repository.cachedPolicy(), includeMaintenance = false)
        }
    }
}
