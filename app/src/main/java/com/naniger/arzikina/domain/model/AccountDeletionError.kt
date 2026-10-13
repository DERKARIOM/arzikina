package com.naniger.arzikina.domain.model

/**
 * Raisons d'échec typées de la suppression définitive du compte (voir
 * [com.naniger.arzikina.domain.repository.UnifiedAuthRepository.deleteAccount]).
 *
 * Distincte de [AuthError]/[SyncAuthError]/[UnifiedAuthError] : cette opération touche À LA FOIS
 * le compte serveur (si une session de synchronisation est active) et le compte local, avec une
 * exigence propre à elle — ne JAMAIS effacer les données locales si la suppression côté serveur
 * n'a pas pu être confirmée (voir la KDoc de
 * [com.naniger.arzikina.data.repository.UnifiedAuthRepositoryImpl.deleteAccount]).
 */
sealed class AccountDeletionError {
    /** Mot de passe incorrect — vérifié localement d'abord ([PasswordHasher][com.naniger.arzikina.util.PasswordHasher]),
     *  puis (si une session serveur existe) également par `delete_account.php` (HTTP 401). */
    data object IncorrectPassword : AccountDeletionError()

    /**
     * Une session de synchronisation est active mais le serveur est inatteignable (pas de réseau,
     * timeout) : la suppression est ENTIÈREMENT annulée plutôt que d'effacer les données locales en
     * laissant les données serveur intactes sous le même mot de passe — l'utilisateur doit réessayer
     * une fois en ligne.
     */
    data object NetworkUnavailable : AccountDeletionError()

    /** Le serveur a répondu mais avec une erreur (5xx, ou un code d'erreur métier inattendu). */
    data class ServerError(val message: String? = null) : AccountDeletionError()

    /** Erreur technique imprévue (désérialisation, etc.). */
    data class Unknown(val cause: Throwable? = null) : AccountDeletionError()
}
