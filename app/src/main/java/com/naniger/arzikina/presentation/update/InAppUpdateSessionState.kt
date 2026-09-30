package com.naniger.arzikina.presentation.update

import com.naniger.arzikina.domain.update.InAppUpdatePolicy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mémoire de la mise à jour pendant CE lancement de l'app. `@Singleton` (et non lié à l'Activity)
 * pour survivre à une rotation ou à une recréation de `MainActivity` : sans cela, tourner l'écran
 * après un « Plus tard » ferait réapparaître la proposition. Volontairement non persistée : un
 * nouveau démarrage de l'app repart de zéro (la sourdine longue durée, elle, est dans
 * `InAppUpdatePromptStore`).
 *
 * Utilisé uniquement depuis le thread principal (callbacks Activity / Play Tasks).
 */
@Singleton
class InAppUpdateSessionState @Inject constructor() {

    /** Voir [InAppUpdatePolicy.SessionFlags.immediateDeclined]. */
    var immediateDeclined: Boolean = false

    /** Voir [InAppUpdatePolicy.SessionFlags.flexibleHandled]. */
    var flexibleHandled: Boolean = false

    /** Flux Google Play en cours d'affichage : permet d'interpréter son résultat même si l'Activity
     * a été recréée entre-temps. `null` si aucun. */
    var launchedFlow: LaunchedFlow? = null

    fun toPolicyFlags() = InAppUpdatePolicy.SessionFlags(
        immediateDeclined = immediateDeclined,
        flexibleHandled = flexibleHandled
    )

    /** @property updateType `AppUpdateType.FLEXIBLE` ou `AppUpdateType.IMMEDIATE`. */
    data class LaunchedFlow(val updateType: Int, val versionCode: Int)
}
