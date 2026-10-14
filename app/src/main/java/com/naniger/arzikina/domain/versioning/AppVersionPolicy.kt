package com.naniger.arzikina.domain.versioning

/**
 * Politique de version publiée par le serveur (console Arzikina Web, « Versions Android »), telle
 * que l'app en a besoin pour décider si elle peut être utilisée. Aucune dépendance Android ni
 * réseau : c'est du domaine pur, testable en JVM.
 *
 * @property minVersionCode plus petit `versionCode` (entier Android) autorisé quand [forceUpdate]
 *   est actif. Toujours comparé NUMÉRIQUEMENT (jamais sur le `versionName`, « 1.10.0 » > « 1.9.0 »
 *   n'est pas fiable en texte).
 * @property forceUpdate `true` = les versions inférieures à [minVersionCode] sont bloquées.
 * @property minVersionName nom lisible de la version minimale, si le serveur la connaît.
 * @property updateUrl lien de mise à jour configuré par le Super Admin (Google Play).
 * @property latestVersionName dernière version publiée, à titre d'information.
 * @property releaseNotes notes de la dernière version publiée.
 * @property maintenanceEnabled le serveur est en maintenance.
 * @property maintenanceMessage message de maintenance saisi dans la console, éventuellement vide.
 */
data class AppVersionPolicy(
    val minVersionCode: Int,
    val forceUpdate: Boolean,
    val minVersionName: String?,
    val updateUrl: String,
    val latestVersionName: String?,
    val releaseNotes: String?,
    val maintenanceEnabled: Boolean,
    val maintenanceMessage: String?
)

/** Version installée sur l'appareil. */
data class InstalledVersion(val versionCode: Int, val versionName: String)

/** Ce que l'app doit faire suite à l'évaluation de la politique (voir [VersionGate]). */
sealed interface VersionGateResult {

    /** Utilisation normale. */
    data object Allowed : VersionGateResult

    /** Version trop ancienne : l'app est bloquée jusqu'à la mise à jour. */
    data class UpdateRequired(
        val installed: InstalledVersion,
        val minVersionCode: Int,
        val minVersionName: String?,
        val updateUrl: String,
        val releaseNotes: String?
    ) : VersionGateResult

    /** Serveur en maintenance (information fraîche uniquement, voir [VersionGate.evaluate]). */
    data class Maintenance(val message: String?) : VersionGateResult
}
