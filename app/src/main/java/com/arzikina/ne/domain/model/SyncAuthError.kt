package com.arzikina.ne.domain.model

/**
 * Raisons d'échec typées de la connexion au serveur de synchronisation (voir
 * [com.arzikina.ne.domain.repository.SyncAuthRepository]).
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

    /** Aucune connexion réseau, timeout, serveur injoignable (DNS, LAN hors de portée...). */
    data object NetworkUnavailable : SyncAuthError()

    /** Le serveur a répondu mais avec une erreur (5xx, ou un code d'erreur métier inattendu). */
    data class ServerError(val message: String? = null) : SyncAuthError()

    /** Erreur technique imprévue (désérialisation, etc.). */
    data class Unknown(val cause: Throwable? = null) : SyncAuthError()
}
