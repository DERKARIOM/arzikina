package com.naniger.arzikina.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Corps de `POST /api/auth/google_login.php` — voir `server/api/auth/google_login.php` pour le
 * contrat exact. [idToken] : jeton d'identité Google obtenu via Credential Manager (voir
 * [com.naniger.arzikina.presentation.auth.GoogleSignInLauncher]), jamais vérifié côté client — la
 * vérification réelle (signature, audience, e-mail vérifié) a lieu UNIQUEMENT côté serveur (voir
 * `server/api/utils/google_token_verifier.php`). [deviceId]/[deviceLabel] : même rôle que
 * [LoginRequestDto], colonnes `auth_tokens.device_id`/`device_label`.
 */
@Serializable
data class GoogleLoginRequestDto(
    val idToken: String,
    val deviceId: String? = null,
    val deviceLabel: String? = null
)
