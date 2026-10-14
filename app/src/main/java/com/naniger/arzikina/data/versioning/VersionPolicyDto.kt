package com.naniger.arzikina.data.versioning

import kotlinx.serialization.Serializable

/**
 * Réponse de `GET api/app/version_policy.php` (voir `server/api/app/version_policy.php`). Tous les
 * champs ont une valeur par défaut : un champ ajouté ou absent côté serveur ne doit jamais faire
 * échouer la lecture (`ignoreUnknownKeys` couvre l'ajout, les défauts couvrent l'absence).
 * Les champs `updateRequired`/`updateAvailable` calculés par le serveur sont volontairement ignorés :
 * la décision locale repose sur la comparaison numérique de `versionCode` (voir `VersionGate`).
 */
@Serializable
internal data class VersionPolicyDto(
    val minVersionCode: Int = 0,
    val minVersionName: String? = null,
    val latestVersionName: String? = null,
    val forceUpdate: Boolean = false,
    val updateUrl: String = "",
    val releaseNotes: String? = null,
    val maintenance: MaintenanceDto = MaintenanceDto()
) {
    @Serializable
    internal data class MaintenanceDto(
        val enabled: Boolean = false,
        val message: String? = null
    )
}
