package com.naniger.arzikina.presentation.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.naniger.arzikina.data.remote.GoogleAuthConfig
import java.security.SecureRandom

/**
 * Point UNIQUE d'appel à Credential Manager pour « Continuer avec Google » — partagé par
 * [LoginFragment] et [RegisterFragment] (cahier des charges : bouton disponible sur les deux
 * écrans) plutôt que dupliqué deux fois.
 *
 * Reste volontairement côté PRÉSENTATION (jamais data/domain) : `CredentialManager.getCredential`
 * exige un `Context` d'Activity pour afficher le sélecteur de compte système — une contrainte
 * d'API Android, pas un détail de la couche data. Le jeton qu'il retourne est BRUT, jamais vérifié
 * ici : seul `UnifiedAuthRepository.loginWithGoogle` (qui l'envoie au backend) produit un résultat
 * de confiance — cahier des charges : « Ne considère jamais qu'un simple e-mail ou un nom transmis
 * par le client suffit à authentifier un utilisateur ».
 *
 * API moderne « Sign in with Google via Credential Manager » (androidx.credentials + googleid) —
 * PAS l'ancien `GoogleSignInClient`/`GoogleSignInOptions`, déprécié par Google. Aucun lien avec
 * Firebase Authentication (non utilisé dans ce projet, voir `GoogleAuthConfig`).
 */
class GoogleSignInLauncher(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    sealed interface Result {
        data class Success(val idToken: String) : Result

        /** L'utilisateur a fermé la fenêtre Google sans choisir de compte — cahier des charges,
         *  Cas D : rester sur l'écran, aucune erreur bloquante, aucun appel serveur. */
        data object Cancelled : Result

        data class Error(val cause: Throwable) : Result
    }

    /**
     * Affiche TOUS les comptes Google de l'appareil (`filterByAuthorizedAccounts = false`), pas
     * seulement ceux déjà utilisés avec Arzikina : sans cela, un utilisateur ayant déjà un compte
     * lié ne verrait jamais les autres comptes de son téléphone et ne pourrait ni en choisir un
     * autre pour se connecter, ni créer un nouveau compte avec une autre adresse. Le compte
     * habituel reste proposé dans la liste. Une seule fenêtre de sélection, centralisée ici pour
     * [LoginFragment], [RegisterFragment] et l'écran Paramètres.
     */
    suspend fun chooseGoogleAccount(): Result = signIn(filterByAuthorizedAccounts = false)

    private suspend fun signIn(filterByAuthorizedAccounts: Boolean): Result {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setServerClientId(GoogleAuthConfig.WEB_CLIENT_ID)
            // Valeur aléatoire non vide (exigée par l'API) — simple garde anti-rejeu côté client ;
            // la vérification qui compte réellement (signature, audience, expiration) est côté
            // serveur, voir `google_token_verifier.php`.
            .setNonce(randomNonce())
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val response = credentialManager.getCredential(context, request)
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(response.credential.data)
            Result.Success(googleIdTokenCredential.idToken)
        } catch (e: GetCredentialCancellationException) {
            Result.Cancelled
        } catch (e: GetCredentialException) {
            Result.Error(e)
        }
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
