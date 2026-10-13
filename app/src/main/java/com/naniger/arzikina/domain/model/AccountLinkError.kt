package com.naniger.arzikina.domain.model

/**
 * Raisons d'échec typées de [com.naniger.arzikina.domain.repository.UnifiedAuthRepository.linkGoogleAccount]
 * — voir sa KDoc et [AccountLinkResult]. Distincte de [UnifiedAuthError] : lier un compte Google
 * n'est ni une connexion ni une inscription, les erreurs possibles ne sont pas les mêmes (pas de
 * `InvalidCredentials`/`EmailAlreadyExists` ici, par exemple).
 */
sealed class AccountLinkError {
    /** Aucune session de synchronisation active : l'utilisateur doit d'abord se connecter
     *  classiquement (cahier des charges, Cas C) avant de pouvoir lier un compte Google. */
    data object NoActiveSession : AccountLinkError()

    /** Ce compte Google (même identifiant `sub`) est déjà associé à un AUTRE compte Arzikina —
     *  voir `server/api/auth/link_google_account.php`. */
    data object GoogleAccountAlreadyLinked : AccountLinkError()

    data object NetworkUnavailable : AccountLinkError()

    data class ServerError(val message: String? = null) : AccountLinkError()

    data class Unknown(val cause: Throwable? = null) : AccountLinkError()
}
