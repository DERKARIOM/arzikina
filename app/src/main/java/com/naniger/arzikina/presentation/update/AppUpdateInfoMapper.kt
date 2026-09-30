package com.naniger.arzikina.presentation.update

import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.naniger.arzikina.domain.update.AppUpdateSnapshot

/** Traduit la réponse de Google Play en [AppUpdateSnapshot] du domaine (seul point de contact entre
 * les constantes Play et `InAppUpdatePolicy`). */
internal fun AppUpdateInfo.toSnapshot(): AppUpdateSnapshot = AppUpdateSnapshot(
    availability = when (updateAvailability()) {
        UpdateAvailability.UPDATE_AVAILABLE -> AppUpdateSnapshot.Availability.AVAILABLE
        UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ->
            AppUpdateSnapshot.Availability.DEVELOPER_TRIGGERED_IN_PROGRESS
        UpdateAvailability.UPDATE_NOT_AVAILABLE -> AppUpdateSnapshot.Availability.NOT_AVAILABLE
        else -> AppUpdateSnapshot.Availability.UNKNOWN
    },
    installStatus = installStatus().toInstallState(),
    availableVersionCode = availableVersionCode(),
    priority = updatePriority(),
    stalenessDays = clientVersionStalenessDays(),
    isFlexibleAllowed = isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
    isImmediateAllowed = isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
)

/** Voir [InstallStatus] : les statuts inconnus ou sans effet sur la décision deviennent `NONE`. */
internal fun Int.toInstallState(): AppUpdateSnapshot.InstallState = when (this) {
    InstallStatus.PENDING -> AppUpdateSnapshot.InstallState.PENDING
    InstallStatus.DOWNLOADING -> AppUpdateSnapshot.InstallState.DOWNLOADING
    InstallStatus.DOWNLOADED -> AppUpdateSnapshot.InstallState.DOWNLOADED
    InstallStatus.INSTALLING -> AppUpdateSnapshot.InstallState.INSTALLING
    InstallStatus.INSTALLED -> AppUpdateSnapshot.InstallState.INSTALLED
    InstallStatus.FAILED -> AppUpdateSnapshot.InstallState.FAILED
    InstallStatus.CANCELED -> AppUpdateSnapshot.InstallState.CANCELED
    else -> AppUpdateSnapshot.InstallState.NONE
}
