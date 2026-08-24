package com.arzikina.ne.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Réponse de `POST /api/auth/login.php` — déjà en camelCase côté serveur (`sendJson` direct,
 * sans passer par `toCamelCaseRow()` : cette dernière ne sert qu'aux lignes issues de MySQL,
 * `login.php` construit son tableau de réponse à la main). [expiresAt] est un timestamp en
 * millisecondes (epoch), à comparer directement à `System.currentTimeMillis()`.
 */
@Serializable
data class LoginResponseDto(
    val token: String,
    val userId: String,
    val expiresAt: Long
)
