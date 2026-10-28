package com.naniger.arzikina.data.remote.dto

import kotlinx.serialization.Serializable

/** Corps de `POST api/auth/set_usage_mode.php` (voir sa doc de tête). */
@Serializable
data class SetUsageModeRequestDto(val usageMode: String)

/** Réponse de `set_usage_mode.php` : le mode FAISANT FOI côté serveur et « le serveur a déjà des comptes ». */
@Serializable
data class SetUsageModeResponseDto(
    val usageMode: String? = null,
    val hasAccounts: Boolean = true
)
