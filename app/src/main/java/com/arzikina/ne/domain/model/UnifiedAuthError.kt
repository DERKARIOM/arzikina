package com.arzikina.ne.domain.model

/**
 * Raisons d'échec typées du login unifié (voir
 * [com.arzikina.ne.domain.repository.UnifiedAuthRepository]) — point d'entrée UNIQUE désormais
 * exposé à la présentation pour se connecter/s'inscrire (voir cahier des charges "audit auth +
 * sync + doublons", étape D). Distincte de [AuthError] (authentification LOCALE historique) et de
 * [SyncAuthError] (connexion au serveur seul) : celles-ci restent des détails d'implémentation de
 * [com.arzikina.ne.data.repository.UnifiedAuthRepositoryImpl], jamais remontées telles quelles à la
 * présentation.
 */
sealed class UnifiedAuthError {
    /** Identifiant/mot de passe incorrects — ne distingue jamais lequel des deux (anti-énumération,
     *  même principe que `login.php`). */
    data object InvalidCredentials : UnifiedAuthError()

    /** Inscription : un compte existe déjà avec cet e-mail sur le serveur — l'utilisateur doit se
     *  connecter plutôt que créer un second compte. */
    data object EmailAlreadyExists : UnifiedAuthError()

    /**
     * Inscription : le nom d'utilisateur CHOISI par l'utilisateur est déjà pris sur le serveur.
     * Jamais renvoyée par [com.arzikina.ne.data.repository.UnifiedAuthRepositoryImpl.login]/la
     * migration silencieuse, qui dérivent un nom invisible pour l'utilisateur et retentent en
     * silence en cas de collision (voir `registerOnServerWithUsernameRetry`) — ici, au contraire,
     * le nom fait partie du formulaire rempli consciemment par l'utilisateur : lui substituer un
     * autre nom sans le prévenir serait trompeur, l'erreur doit donc lui être remontée pour qu'il
     * en choisisse un autre lui-même.
     */
    data object UsernameAlreadyExists : UnifiedAuthError()

    data class ValidationFailed(val reason: ValidationReason) : UnifiedAuthError() {
        /** [INVALID_USERNAME]/[SECURITY_ANSWER_TOO_SHORT] : ajoutées avec l'inscription complète
         *  (voir [com.arzikina.ne.domain.repository.UnifiedAuthRepository.register]) — sans objet
         *  pour [com.arzikina.ne.domain.repository.UnifiedAuthRepository.login], qui ne les valide
         *  jamais. */
        enum class ValidationReason {
            REQUIRED_FIELD_MISSING, INVALID_EMAIL_FORMAT, PASSWORD_TOO_SHORT,
            INVALID_USERNAME, SECURITY_ANSWER_TOO_SHORT
        }
    }

    /**
     * Pas de réseau ET aucun compte local déjà connu sur CET appareil (ou mot de passe local ne
     * correspondant pas) pour continuer hors-ligne — voir
     * [com.arzikina.ne.data.repository.UnifiedAuthRepositoryImpl.attemptOfflineFallback]. Le
     * cahier des charges (section 13) demande explicitement de permettre de continuer hors-ligne
     * "si cela est compatible avec la logique de sécurité" : ce cas précis est celui où ce n'est
     * PAS possible (première connexion de cet appareil, jamais authentifié avant).
     */
    data object NetworkUnavailableNoLocalFallback : UnifiedAuthError()

    data class ServerError(val message: String? = null) : UnifiedAuthError()

    data class Unknown(val cause: Throwable? = null) : UnifiedAuthError()
}
