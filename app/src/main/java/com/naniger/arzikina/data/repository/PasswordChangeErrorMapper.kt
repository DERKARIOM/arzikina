package com.naniger.arzikina.data.repository

import com.naniger.arzikina.domain.model.PasswordChangeError

/**
 * Traduit une réponse d'erreur de `set_password.php` en [PasswordChangeError]. Le corps HTTP brut est cherché
 * par contenu textuel (même idiome que `SyncAuthRepositoryImpl.mapGoogleAuthError`) ; les codes d'erreur sont
 * stables côté serveur.
 *
 * Un 401 est ambigu côté serveur (mot de passe actuel faux, jeton Google refusé ou session expirée) : la
 * preuve utilisée ([usedGoogleProof]) et les codes de session (`invalid_token`, `missing_token`) lèvent le doute.
 */
internal object PasswordChangeErrorMapper {

    fun map(httpCode: Int, body: String?, usedGoogleProof: Boolean): PasswordChangeError {
        val text = body.orEmpty()
        return when {
            "password_too_short" in text || "password_too_long" in text -> PasswordChangeError.InvalidNewPassword
            "google_identity_mismatch" in text -> PasswordChangeError.GoogleIdentityMismatch
            // Session (Bearer) refusée : à tester AVANT le cas 401 générique ci-dessous.
            "invalid_token" in text || "missing_token" in text -> PasswordChangeError.NoActiveSession
            httpCode == 401 ->
                if (usedGoogleProof) PasswordChangeError.InvalidGoogleToken else PasswordChangeError.IncorrectCurrentPassword
            else -> PasswordChangeError.ServerError(body)
        }
    }
}
