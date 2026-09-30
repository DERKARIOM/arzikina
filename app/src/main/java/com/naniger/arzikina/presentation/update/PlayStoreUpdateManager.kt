package com.naniger.arzikina.presentation.update

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.ActivityResult as PlayActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.naniger.arzikina.R
import com.naniger.arzikina.data.update.PlayStoreInstallSource
import com.naniger.arzikina.domain.repository.InAppUpdatePromptStore
import com.naniger.arzikina.domain.update.InAppUpdateDecision
import com.naniger.arzikina.domain.update.InAppUpdatePolicy
import com.naniger.arzikina.domain.update.UpdatePromptDismissal
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Intégration de Google Play In-App Updates (mécanisme officiel, voir
 * https://developer.android.com/guide/playcore/in-app-updates) pour `MainActivity`, la seule Activity
 * de l'app. Toute la logique de mise à jour vit ici, pas dans l'Activity : celle-ci n'appelle que
 * [attach] et [setPromptsAllowed].
 *
 * Répartition des responsabilités :
 * - QUAND et QUOI proposer : [InAppUpdatePolicy] (domaine, testée en JVM pur) ;
 * - « Plus tard » mémorisé plusieurs jours : [InAppUpdatePromptStore] ;
 * - pas de répétition pendant un même lancement (rotation incluse) : [InAppUpdateSessionState] ;
 * - dialogue de proposition : [InAppUpdateOfferDialogFragment] ;
 * - ici : dialogue avec Google Play et cycle de vie.
 *
 * Cycle de vie :
 * - vérification à chaque `onResume` (lancement, retour au premier plan, retour du flux Play),
 *   comme le recommande Google — c'est ce qui détecte un téléchargement terminé en arrière-plan ou
 *   une mise à jour Immediate interrompue. Une seule vérification à la fois ([isChecking]) ;
 * - le résultat d'un flux Play est interprété même si l'Activity a été recréée (voir
 *   [InAppUpdateSessionState.launchedFlow]) ;
 * - l'écouteur de téléchargement est retiré à la destruction de l'Activity (aucune fuite).
 *
 * Installation hors Google Play (APK, Android Studio, autre magasin) : [attach] ne fait rien, Play
 * n'est jamais interrogé et rien n'est affiché (voir [PlayStoreInstallSource]).
 *
 * Aucune erreur n'empêche d'utiliser l'app : échec de connexion, Play indisponible, appareil hors
 * ligne → rien n'est affiché ; seul un échec APRÈS que l'utilisateur a accepté la mise à jour est
 * signalé (message discret).
 */
@ActivityScoped
class PlayStoreUpdateManager @Inject constructor(
    private val activity: FragmentActivity,
    private val appUpdateManager: AppUpdateManager,
    private val installSource: PlayStoreInstallSource,
    private val promptStore: InAppUpdatePromptStore,
    private val policy: InAppUpdatePolicy,
    private val session: InAppUpdateSessionState
) : DefaultLifecycleObserver {

    private lateinit var updateFlowLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var snackbarHost: View? = null
    private var snackbarAnchor: () -> View? = { null }
    private var isAttached = false
    private var isChecking = false

    /** Voir [setPromptsAllowed]. */
    private var promptsAllowed = false

    /** Proposition reportée car un écran de connexion/verrouillage était affiché. */
    private var deferredPrompt: InAppUpdateDecision? = null
    private var restartSnackbar: Snackbar? = null

    private val installStateListener = InstallStateUpdatedListener { state ->
        onInstallStatusChanged(state.installStatus(), state.installErrorCode())
    }

    /**
     * À appeler dans `onCreate` (obligatoire : [ActivityResultLauncher] doit être enregistré avant
     * `onStart`).
     *
     * @param snackbarHost vue racine pour les messages (« Redémarrer », échec).
     * @param snackbarAnchor vue au-dessus de laquelle placer ces messages (Bottom Navigation), ou
     *   `null` si elle est masquée.
     */
    fun attach(snackbarHost: View, snackbarAnchor: () -> View?) {
        if (isAttached || !installSource.isInstalledFromPlayStore) return
        isAttached = true
        this.snackbarHost = snackbarHost
        this.snackbarAnchor = snackbarAnchor

        updateFlowLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result -> onUpdateFlowResult(result) }
        activity.supportFragmentManager.setFragmentResultListener(
            InAppUpdateOfferDialogFragment.REQUEST_KEY,
            activity
        ) { _, result -> onOfferResult(result) }
        appUpdateManager.registerListener(installStateListener)
        activity.lifecycle.addObserver(this)
    }

    /**
     * `false` pendant les écrans de connexion et de verrouillage biométrique : les propositions
     * (dialogue, « Redémarrer ») y sont reportées jusqu'à l'écran suivant. Le flux Immediate, lui,
     * n'est pas reporté : c'est un écran plein cadre de Google Play qui n'expose aucune donnée.
     */
    fun setPromptsAllowed(allowed: Boolean) {
        promptsAllowed = allowed
        if (allowed && isActive()) {
            deferredPrompt?.let { prompt ->
                deferredPrompt = null
                present(prompt)
            }
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        checkForUpdate()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        appUpdateManager.unregisterListener(installStateListener)
        restartSnackbar?.dismiss()
        restartSnackbar = null
        snackbarHost = null
    }

    // --- Vérification ---------------------------------------------------------------------------

    private fun checkForUpdate() {
        if (!isAttached || isChecking || session.launchedFlow != null) return
        isChecking = true
        appUpdateManager.appUpdateInfo
            .addOnCompleteListener { isChecking = false }
            .addOnSuccessListener { info -> if (isActive()) onUpdateInfo(info) }
            // Play indisponible, hors ligne, app non reconnue par Play… : silencieux, l'app continue.
            .addOnFailureListener { error -> Log.w(TAG, "Vérification de mise à jour impossible", error) }
    }

    private fun onUpdateInfo(info: AppUpdateInfo) {
        val snapshot = info.toSnapshot()
        activity.lifecycleScope.launch {
            val decision = policy.decide(
                snapshot = snapshot,
                lastDismissal = promptStore.lastDismissal(),
                nowMillis = System.currentTimeMillis(),
                session = session.toPolicyFlags()
            )
            if (!isActive()) return@launch
            when (decision) {
                InAppUpdateDecision.None -> Unit
                InAppUpdateDecision.ResumeImmediate,
                InAppUpdateDecision.StartImmediate -> startUpdateFlow(info, AppUpdateType.IMMEDIATE)
                InAppUpdateDecision.PromptInstallDownloaded,
                is InAppUpdateDecision.OfferFlexible -> present(decision)
            }
        }
    }

    /** Affiche une proposition, ou la reporte si un écran de connexion/verrouillage est affiché. */
    private fun present(prompt: InAppUpdateDecision) {
        if (!promptsAllowed) {
            deferredPrompt = prompt
            return
        }
        when (prompt) {
            InAppUpdateDecision.PromptInstallDownloaded -> showRestartSnackbar()
            is InAppUpdateDecision.OfferFlexible -> InAppUpdateOfferDialogFragment.showIfAbsent(
                activity.supportFragmentManager,
                prompt.versionCode
            )
            else -> Unit
        }
    }

    // --- Proposition Flexible -------------------------------------------------------------------

    private fun onOfferResult(result: Bundle) {
        session.flexibleHandled = true
        val versionCode = result.getInt(InAppUpdateOfferDialogFragment.RESULT_VERSION_CODE)
        val choice = result.getString(InAppUpdateOfferDialogFragment.RESULT_CHOICE)
        if (choice == InAppUpdateOfferDialogFragment.Choice.UPDATE.name) {
            startFlexibleUpdateAfterConsent()
        } else {
            recordDismissal(versionCode)
        }
    }

    /** Un [AppUpdateInfo] ne sert qu'une fois et a pu vieillir pendant l'affichage du dialogue :
     * on redemande l'état à Play juste avant de lancer le flux. */
    private fun startFlexibleUpdateAfterConsent() {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (!isActive()) return@addOnSuccessListener
                val stillAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                if (stillAvailable) startUpdateFlow(info, AppUpdateType.FLEXIBLE)
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Impossible de lancer la mise à jour Flexible", error)
                showMessage(R.string.in_app_update_failed_message)
            }
    }

    // --- Flux Google Play -----------------------------------------------------------------------

    private fun startUpdateFlow(info: AppUpdateInfo, updateType: Int) {
        if (session.launchedFlow != null) return
        val started = runCatching {
            appUpdateManager.startUpdateFlowForResult(
                info,
                updateFlowLauncher,
                AppUpdateOptions.defaultOptions(updateType)
            )
        }.onFailure { error -> Log.w(TAG, "Flux de mise à jour non lancé", error) }
            .getOrDefault(false)

        if (started) {
            session.launchedFlow = InAppUpdateSessionState.LaunchedFlow(updateType, info.availableVersionCode())
        } else if (updateType == AppUpdateType.IMMEDIATE) {
            // Évite de retenter en boucle à chaque onResume pendant ce lancement.
            session.immediateDeclined = true
        }
    }

    private fun onUpdateFlowResult(result: ActivityResult) {
        val flow = session.launchedFlow
        session.launchedFlow = null
        val updateType = flow?.updateType

        when (result.resultCode) {
            // Flexible : le téléchargement démarre, suivi par installStateListener.
            // Immediate : Google Play installe puis relance l'app lui-même.
            Activity.RESULT_OK -> if (updateType == AppUpdateType.FLEXIBLE) session.flexibleHandled = true

            Activity.RESULT_CANCELED -> {
                markHandled(updateType)
                // Refus dans l'interface Google Play = même effet que « Plus tard ».
                if (updateType == AppUpdateType.FLEXIBLE && flow != null) recordDismissal(flow.versionCode)
            }

            else -> {
                if (result.resultCode == PlayActivityResult.RESULT_IN_APP_UPDATE_FAILED) {
                    Log.w(TAG, "Échec du flux de mise à jour Google Play")
                }
                markHandled(updateType)
                showMessage(R.string.in_app_update_failed_message)
            }
        }
    }

    /** Aucune nouvelle proposition du même type pendant ce lancement (type inconnu : les deux). */
    private fun markHandled(updateType: Int?) {
        if (updateType != AppUpdateType.FLEXIBLE) session.immediateDeclined = true
        if (updateType != AppUpdateType.IMMEDIATE) session.flexibleHandled = true
    }

    // --- Téléchargement Flexible ----------------------------------------------------------------

    private fun onInstallStatusChanged(status: Int, errorCode: Int) {
        when (status) {
            // App en arrière-plan : le prochain onResume détectera DOWNLOADED et proposera.
            InstallStatus.DOWNLOADED -> if (isActive()) present(InAppUpdateDecision.PromptInstallDownloaded)
            InstallStatus.FAILED -> {
                Log.w(TAG, "Échec du téléchargement de la mise à jour (code $errorCode)")
                if (isActive()) showMessage(R.string.in_app_update_failed_message)
            }
            InstallStatus.INSTALLED -> restartSnackbar?.dismiss()
            // CANCELED (annulé depuis la notification Play), PENDING, DOWNLOADING… : rien à afficher.
            else -> Unit
        }
    }

    private fun showRestartSnackbar() {
        if (restartSnackbar?.isShownOrQueued == true) return
        val host = snackbarHost ?: return
        restartSnackbar = Snackbar.make(host, R.string.in_app_update_downloaded_message, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.in_app_update_action_restart) { completeUpdate() }
            .setAnchorView(snackbarAnchor())
            .also { it.show() }
    }

    /** Installe la mise à jour téléchargée : Google Play redémarre l'app. */
    private fun completeUpdate() {
        appUpdateManager.completeUpdate().addOnFailureListener { error ->
            Log.w(TAG, "Installation de la mise à jour impossible", error)
            if (isActive()) showMessage(R.string.in_app_update_failed_message)
        }
    }

    // --- Utilitaires ----------------------------------------------------------------------------

    private fun recordDismissal(versionCode: Int) {
        activity.lifecycleScope.launch {
            promptStore.recordDismissal(UpdatePromptDismissal(versionCode, System.currentTimeMillis()))
        }
    }

    private fun showMessage(@StringRes messageRes: Int) {
        val host = snackbarHost ?: return
        Snackbar.make(host, messageRes, Snackbar.LENGTH_LONG)
            .setAnchorView(snackbarAnchor())
            .show()
    }

    private fun isActive(): Boolean = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private companion object {
        const val TAG = "PlayStoreUpdate"
    }
}
