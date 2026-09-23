package com.naniger.arzikina.domain.model

/**
 * Raisons d'échec typées de la connexion au serveur de synchronisation (voir
 * [com.naniger.arzikina.domain.repository.SyncAuthRepository]).
 *
 * Délibérément séparée de [AuthError] : celle-ci couvre l'authentification LOCALE (Room +
 * PBKDF2, écrans Inscription/Connexion/Mot de passe oublié) et ses erreurs de validation de
 * formulaire ; [SyncAuthError] couvre la connexion au serveur distant (voir `server/api/auth/login.php`)
 * et ses erreurs réseau/serveur, qui n'ont pas de sens pour l'authentification locale
 * (`NetworkUnavailable`, `ServerError` n'existent pas côté local — tout y est synchrone sur
 * Room). Mélanger les deux aurait forcé chaque appelant de [AuthError] à gérer des cas qui ne le
 * concernent jamais.
 */
sealed class SyncAuthError {
    /** Identifiant ou mot de passe incorrect (HTTP 401, voir `login.php`). */
    data object InvalidCredentials : SyncAuthError()

    /** Nom d'utilisateur déjà pris côté serveur (HTTP 409 `username_taken`, voir `register.php`) —
     *  ne devrait normalement pas atteindre l'utilisateur final : voir
     *  [com.naniger.arzikina.data.repository.UnifiedAuthRepositoryImpl], qui dérive le nom d'utilisateur
     *  automatiquement et retente en cas de collision plutôt que d'exposer cette erreur. */
    data object UsernameTaken : SyncAuthError()

    /** Adresse e-mail déjà associée à un compte serveur (HTTP 409 `email_taken`, voir
     *  `register.php`) — celle-ci, contrairement à [UsernameTaken], EST attendue à l'écran
     *  d'inscription (l'utilisateur doit alors se connecter plutôt que créer un second compte). */
    data object EmailTaken : SyncAuthError()

    /** Aucune connexion réseau, timeout, serveur injoignable (DNS, LAN hors de portée...). */
    data object NetworkUnavailable : SyncAuthError()

    /** Le serveur a répondu mais avec une erreur (5xx, ou un code d'erreur métier inattendu). */
    data class ServerError(val message: String? = null) : SyncAuthError()

    /** Erreur technique imprévue (désérialisation, etc.). */
    data class Unknown(val cause: Throwable? = null) : SyncAuthError()
}
