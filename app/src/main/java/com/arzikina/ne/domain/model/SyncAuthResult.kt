package com.arzikina.ne.domain.model

/**
 * Résultat d'une opération de connexion au serveur de synchronisation : succès typé ou échec
 * typé (voir [SyncAuthError]). Même forme que [AuthResult] (succès/échec scellés), mais
 * paramétrée sur [SyncAuthError] plutôt que [AuthError] — les deux hiérarchies d'erreurs ne se
 * recouvrent pas (voir la KDoc de [SyncAuthError]), donc pas de générique commun réutilisable
 * sans perdre cette distinction volontaire.
 */
sealed class SyncAuthResult<out T> {
    data class Success<T>(val data: T) : SyncAuthResult<T>()
    data class Failure(val error: SyncAuthError) : SyncAuthResult<Nothing>()
}

/** Transforme la donnée de succès sans avoir à dérouler manuellement le `when`. */
inline fun <T, R> SyncAuthResult<T>.map(transform: (T) -> R): SyncAuthResult<R> = when (this) {
    is SyncAuthResult.Success -> SyncAuthResult.Success(transform(data))
    is SyncAuthResult.Failure -> this
}
