package com.naniger.arzikina.domain.update

/**
 * État d'une mise à jour tel que RAPPORTÉ PAR GOOGLE PLAY (voir `AppUpdateInfo`), traduit en types
 * du domaine : aucune dépendance à la bibliothèque Play ici, ce qui rend [InAppUpdatePolicy]
 * testable en JVM pur (voir `presentation/update/AppUpdateInfoMapper.kt` pour la traduction).
 *
 * Google Play est la SEULE source de vérité : aucun champ n'est calculé localement (jamais de
 * comparaison entre `versionCode`/`versionName` installés et une version « attendue »).
 *
 * @property availableVersionCode versionCode proposé par Play — utilisé uniquement pour savoir si
 *   un « Plus tard » concernait CETTE version (voir [UpdatePromptDismissal]), jamais affiché.
 * @property priority priorité 0..5 définie à la publication (Play Developer API,
 *   `inAppUpdatePriority`) — 0 si non définie.
 * @property stalenessDays jours écoulés depuis que Play a connaissance de la mise à jour sur cet
 *   appareil, `null` si inconnu.
 */
data class AppUpdateSnapshot(
    val availability: Availability,
    val installStatus: InstallState,
    val availableVersionCode: Int,
    val priority: Int,
    val stalenessDays: Int?,
    val isFlexibleAllowed: Boolean,
    val isImmediateAllowed: Boolean
) {
    /** Miroir de `UpdateAvailability` (Play). */
    enum class Availability {
        NOT_AVAILABLE,
        AVAILABLE,

        /** Une mise à jour lancée par l'app est déjà en cours (Immediate interrompue, ou
         * téléchargement Flexible en cours). */
        DEVELOPER_TRIGGERED_IN_PROGRESS,
        UNKNOWN
    }

    /** Miroir simplifié de `InstallStatus` (Play). */
    enum class InstallState {
        NONE,
        PENDING,
        DOWNLOADING,
        DOWNLOADED,
        INSTALLING,
        INSTALLED,
        FAILED,
        CANCELED;

        /** Un téléchargement/une installation Flexible est déjà en route : rien à proposer. */
        val isInFlight: Boolean get() = this == PENDING || this == DOWNLOADING || this == INSTALLING
    }
}

/** Dernier « Plus tard » de l'utilisateur sur une proposition Flexible (voir
 * [com.naniger.arzikina.domain.repository.InAppUpdatePromptStore]). */
data class UpdatePromptDismissal(
    val versionCode: Int,
    val dismissedAtMillis: Long
)
