package com.arzikina.ne.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Réponse de `POST /api/auth/login.php` ET `POST /api/auth/register.php` (formes IDENTIQUES,
 * volontairement — voir la doc de tête de `register.php` : une inscription réussie émet
 * directement une session, comme une connexion) — déjà en camelCase côté serveur (`sendJson`
 * direct, sans passer par `toCamelCaseRow()` : cette dernière ne sert qu'aux lignes issues de
 * MySQL, ces deux endpoints construisent leur tableau de réponse à la main). [expiresAt] est un
 * timestamp en millisecondes (epoch), à comparer directement à `System.currentTimeMillis()`.
 *
 * [fullName] : nom complet réel (`users.full_name`) — utilisé par [UnifiedAuthRepositoryImpl] pour
 * créer/compléter le profil LOCAL correspondant (voir sa KDoc) lors du premier login unifié sur cet
 * appareil.
 */
@Serializable
data class LoginResponseDto(
    val token: String,
    val userId: String,
    val expiresAt: Long,
    val fullName: String
)
