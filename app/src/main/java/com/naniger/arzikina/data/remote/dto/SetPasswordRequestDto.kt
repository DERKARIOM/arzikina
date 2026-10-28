package com.naniger.arzikina.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Corps de `POST api/auth/set_password.php` : le nouveau mot de passe et UNE preuve (`currentPassword` OU
 * `idToken`, l'autre à `null`). Voir la doc de tête de `set_password.php`.
 */
@Serializable
data class SetPasswordRequestDto(
    val newPassword: String,
    val currentPassword: String?,
    val idToken: String?
)
