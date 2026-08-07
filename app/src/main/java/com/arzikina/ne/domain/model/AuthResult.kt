package com.arzikina.ne.domain.model

/**
 * Résultat d'une opération d'authentification : succès typé ou échec typé
 * (voir [AuthError]). Distinct de [com.arzikina.ne.util.AppResult] (qui
 * modélise un état d'écran avec Loading/Success/Error-texte) : [AuthResult]
 * est la valeur de retour ponctuelle d'un appel suspend au repository
 * (register/login/...), que la présentation transforme ensuite, le cas
 * échéant, en [com.arzikina.ne.util.AppResult] pour son propre état d'écran.
 */
sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Failure(val error: AuthError) : AuthResult<Nothing>()
}

/** Transforme la donnée de succès sans avoir à dérouler manuellement le `when`. */
inline fun <T, R> AuthResult<T>.map(transform: (T) -> R): AuthResult<R> = when (this) {
    is AuthResult.Success -> AuthResult.Success(transform(data))
    is AuthResult.Failure -> this
}
