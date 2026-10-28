package com.naniger.arzikina.domain.model

/**
 * Preuve d'identité exigée pour changer ou définir le mot de passe : un jeton de session volé seul ne doit
 * jamais suffire (même principe que « Supprimer mon compte »).
 */
sealed interface PasswordProof {

    /** L'utilisateur connaît son mot de passe actuel. */
    data class CurrentPassword(val raw: String) : PasswordProof

    /**
     * L'utilisateur n'a pas de mot de passe (compte créé via Google) ou l'a oublié : il confirme son identité avec
     * un jeton d'identité Google FRAIS, vérifié par le serveur (`server/api/auth/set_password.php`) — il doit
     * correspondre au compte Google lié à son compte Arzikina.
     */
    data class GoogleIdToken(val token: String) : PasswordProof
}

/** Limites d'un nouveau mot de passe. Le minimum est celui de [com.naniger.arzikina.util.AuthValidator]. */
object PasswordLimits {
    const val MAX_BYTES = 128

    /** Même mesure que le serveur (`strlen`, en octets) : un mot de passe accentué compte plus que ses caractères. */
    fun isWithinMax(rawPassword: String): Boolean = rawPassword.toByteArray(Charsets.UTF_8).size <= MAX_BYTES
}

sealed class PasswordChangeError {
    /** Le mot de passe actuel fourni est incorrect. */
    data object IncorrectCurrentPassword : PasswordChangeError()

    /** Le nouveau mot de passe est trop court ou trop long. */
    data object InvalidNewPassword : PasswordChangeError()

    /** Le compte Google choisi n'est pas celui associé à ce compte Arzikina. */
    data object GoogleIdentityMismatch : PasswordChangeError()

    /** Jeton Google invalide, expiré ou e-mail Google non vérifié. */
    data object InvalidGoogleToken : PasswordChangeError()

    /** Pas de session utilisable : se reconnecter d'abord. */
    data object NoActiveSession : PasswordChangeError()

    data object NetworkUnavailable : PasswordChangeError()

    data class ServerError(val message: String? = null) : PasswordChangeError()

    data class Unknown(val cause: Throwable? = null) : PasswordChangeError()
}

sealed class PasswordChangeResult {
    data object Success : PasswordChangeResult()
    data class Failure(val error: PasswordChangeError) : PasswordChangeResult()
}
