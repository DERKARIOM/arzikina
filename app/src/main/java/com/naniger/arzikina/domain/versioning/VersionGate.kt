package com.naniger.arzikina.domain.versioning

/**
 * Règle de décision unique : « cette version de l'app a-t-elle le droit de fonctionner ? ».
 * Fonction pure, sans état (testée en JVM, voir `VersionGateTest`).
 *
 * Règles :
 * 1. Bloquée ⇔ `forceUpdate` ET `versionCode installé < minVersionCode` (comparaison d'entiers).
 *    Le serveur applique la MÊME règle côté API : cette vérification locale améliore l'expérience
 *    (écran explicite) mais n'est jamais la seule barrière.
 * 2. Une mise à jour obligatoire passe avant la maintenance : c'est ce que l'utilisateur doit
 *    faire de toute façon.
 * 3. La maintenance n'est retenue que si [evaluate] reçoit `includeMaintenance = true`, c'est-à-dire
 *    pour une réponse FRAÎCHE du serveur. Une politique lue dans le cache hors ligne ne peut pas
 *    affirmer que le serveur est encore en maintenance : enfermer l'utilisateur sur un état périmé
 *    serait pire que de le laisser tenter.
 * 4. Pas de politique (jamais téléchargée, serveur injoignable) → [VersionGateResult.Allowed] : une
 *    panne réseau ne doit jamais bloquer l'app à elle seule.
 */
object VersionGate {

    fun evaluate(
        installed: InstalledVersion,
        policy: AppVersionPolicy?,
        includeMaintenance: Boolean
    ): VersionGateResult {
        if (policy == null) return VersionGateResult.Allowed
        if (policy.forceUpdate && installed.versionCode < policy.minVersionCode) {
            return VersionGateResult.UpdateRequired(
                installed = installed,
                minVersionCode = policy.minVersionCode,
                minVersionName = policy.minVersionName,
                updateUrl = policy.updateUrl,
                releaseNotes = policy.releaseNotes
            )
        }
        if (includeMaintenance && policy.maintenanceEnabled) {
            return VersionGateResult.Maintenance(policy.maintenanceMessage)
        }
        return VersionGateResult.Allowed
    }
}
