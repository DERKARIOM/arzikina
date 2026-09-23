package com.naniger.arzikina.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Corps de `POST /api/auth/login.php` — voir `server/api/auth/login.php` pour le contrat exact.
 * [identifier] : nom d'utilisateur OU e-mail, au choix (le serveur teste les deux). [deviceId]/
 * [deviceLabel] sont optionnels côté serveur (colonnes `auth_tokens.device_id`/`device_label`,
 * utiles pour une future gestion des appareils connectés — pas encore d'écran dédié).
 */
@Serializable
data class LoginRequestDto(
    val identifier: String,
    val password: String,
    val deviceId: String? = null,
    val deviceLabel: String? = null
)
