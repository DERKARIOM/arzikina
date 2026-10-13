package com.naniger.arzikina.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Corps de `POST /api/auth/delete_account.php` — voir `server/api/auth/delete_account.php` pour
 * le contrat exact. [password] : mot de passe en clair RE-DEMANDÉ (même principe que
 * [LoginRequestDto]) — protège contre un token seul (volé, qui traîne dans un log ou le
 * presse-papier) utilisé pour détruire le compte sans jamais connaître le mot de passe.
 */
@Serializable
data class DeleteAccountRequestDto(
    val password: String
)
