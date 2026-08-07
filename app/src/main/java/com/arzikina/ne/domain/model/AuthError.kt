package com.arzikina.ne.domain.model

/**
 * Raisons d'échec typées des opérations d'authentification (voir
 * [com.arzikina.ne.domain.repository.AuthRepository] et [AuthResult]).
 *
 * Une hiérarchie scellée dédiée — plutôt que de réutiliser
 * [com.arzikina.ne.util.AppResult.Error] (message texte libre) — est
 * nécessaire ici car la présentation doit savoir PRÉCISÉMENT quel champ est
 * en cause (ex. surligner le champ "e-mail" plutôt que "nom d'utilisateur"
 * sur l'écran Inscription) et choisir elle-même la chaîne localisée
 * correspondante (`strings.xml`) : le domaine ne doit jamais produire de
 * texte destiné à l'affichage.
 */
sealed class AuthError {
    /** Identifiant (e-mail ou nom d'utilisateur) inconnu, ou mot de passe incorrect.
     *  Volontairement fusionnés en une seule erreur : révéler lequel des deux est
     *  fautif faciliterait l'énumération des comptes existants par un tiers. */
    data object InvalidCredentials : AuthError()

    data object UsernameAlreadyExists : AuthError()
    data object EmailAlreadyExists : AuthError()

    /** Utilisé par la réinitialisation locale du mot de passe (identifiant saisi sans compte associé). */
    data object UserNotFound : AuthError()

    /** Le mot de passe actuel fourni (changement de mot de passe) ne correspond pas. */
    data object CurrentPasswordIncorrect : AuthError()

    /** Réponse incorrecte à la question de sécurité (écran "Mot de passe oublié"). */
    data object SecurityAnswerIncorrect : AuthError()

    /** Échecs de validation de format détectés côté repository (filet de sécurité :
     *  la présentation est censée déjà valider via [com.arzikina.ne.util.AuthValidator]
     *  avant même d'appeler le repository, pour un retour instantané). */
    data class ValidationFailed(val reason: ValidationReason) : AuthError()

    /** Erreur technique imprévue (I/O, contrainte SQLite inattendue...). */
    data class Unknown(val cause: Throwable? = null) : AuthError()

    enum class ValidationReason {
        REQUIRED_FIELD_MISSING,
        INVALID_EMAIL_FORMAT,
        PASSWORD_TOO_SHORT,
        PASSWORDS_DO_NOT_MATCH,
        INVALID_USERNAME_FORMAT,
        SECURITY_ANSWER_TOO_SHORT
    }
}
