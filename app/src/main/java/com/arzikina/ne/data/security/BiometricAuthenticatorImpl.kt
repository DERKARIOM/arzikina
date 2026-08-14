package com.arzikina.ne.data.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.arzikina.ne.R
import com.arzikina.ne.domain.repository.BiometricAuthenticator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Implémentation [BiometricAuthenticator] basée sur `androidx.biometric` (`BiometricPrompt`).
 *
 * `BIOMETRIC_STRONG` uniquement (pas `BIOMETRIC_WEAK` ni `DEVICE_CREDENTIAL`) : cette V1 se limite
 * strictement à l'empreinte digitale/reconnaissance faciale de classe forte, sans repli PIN/schéma
 * — un appareil qui ne propose que de la biométrie faible (rare, surtout sur API 28-29, voir
 * l'analyse préalable) verra simplement [isAvailable] renvoyer `false`, et le réglage restera
 * indisponible dans l'écran Profil plutôt que de proposer un mécanisme dégradé silencieusement
 * moins sûr.
 *
 * `@ApplicationContext` suffit pour [isAvailable] (`BiometricManager.from` n'a besoin que d'un
 * `Context`) ; seul [authenticate] a besoin d'un hôte `FragmentActivity` réel, fourni par l'appelant
 * (voir la doc de l'interface pour le raisonnement sur cette exception d'architecture).
 */
@Singleton
class BiometricAuthenticatorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BiometricAuthenticator {

    override suspend fun isAvailable(): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Convertit le callback de `BiometricPrompt` (pensé pour Java/callbacks) en une simple valeur
     * `Boolean` suspendue, conformément à la doc de l'interface. `onAuthenticationFailed` (empreinte
     * NON reconnue) ne résout PAS la coroutine : le prompt système reste affiché et permet à
     * l'utilisateur de réessayer immédiatement — seule une résolution DÉFINITIVE (succès, ou erreur
     * qui ferme le prompt : annulation, dépassement du nombre d'essais, bouton "Annuler") doit
     * reprendre la coroutine, sous peine de `IllegalStateException` (reprise en double).
     *
     * `withContext(Dispatchers.Main.immediate)` : `BiometricPrompt` (construction ET `authenticate`)
     * doit s'exécuter sur le thread principal (il manipule le `FragmentManager` de [host]) — rien ne
     * garantit que l'appelant de cette fonction `suspend` s'y trouve déjà (voir `Dispatchers.IO`
     * utilisé ailleurs dans le projet pour les accès Room). `.immediate` évite un changement de
     * thread inutile si on y est déjà.
     */
    override suspend fun authenticate(host: FragmentActivity): Boolean = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            // Garde d'entrée : si la coroutine est déjà annulée avant même d'afficher le prompt
            // (cas rare mais possible avec suspendCancellableCoroutine), ne pas l'afficher pour
            // rien — `invokeOnCancellation` seul ne suffit pas à empêcher l'affichage initial.
            if (!continuation.isActive) return@suspendCancellableCoroutine

            val executor = ContextCompat.getMainExecutor(context)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) continuation.resume(false)
                }

                override fun onAuthenticationFailed() {
                    // Volontairement vide — voir KDoc ci-dessus.
                }
            }
            val prompt = BiometricPrompt(host, executor, callback)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.biometric_prompt_title))
                .setSubtitle(context.getString(R.string.biometric_prompt_subtitle))
                .setNegativeButtonText(context.getString(R.string.biometric_prompt_negative_button))
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .build()

            // Annulation du prompt si la coroutine appelante est annulée (ex. Fragment détruit
            // pendant la vérification) : évite un `BiometricPrompt` fantôme qui continuerait
            // d'attendre une empreinte sur un écran déjà quitté.
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }

            prompt.authenticate(promptInfo)
        }
    }
}
