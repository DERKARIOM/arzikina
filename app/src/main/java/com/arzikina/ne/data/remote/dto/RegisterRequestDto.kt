package com.arzikina.ne.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Corps de `POST /api/auth/register.php` — voir `server/api/auth/register.php` pour le contrat
 * exact. [username] est TOUJOURS fourni ici (dérivé automatiquement de l'e-mail par
 * [com.arzikina.ne.data.repository.UnifiedAuthRepositoryImpl] si l'utilisateur n'en a pas saisi un
 * explicitement — voir sa KDoc) : le serveur l'exige (colonne `users.username`, `UNIQUE`), mais le
 * nouveau parcours simplifié ("Gmail + mot de passe") ne le demande plus à l'écran.
 *
 * [securityQuestion]/[securityAnswer] optionnels — voir la doc de tête de `register.php` (repli
 * aléatoire côté serveur quand absents, aucune fonctionnalité de récupération de mot de passe
 * serveur n'en dépend encore).
 */
@Serializable
data class RegisterRequestDto(
    val fullName: String,
    val username: String,
    val email: String,
    val phoneNumber: String? = null,
    val password: String,
    val securityQuestion: String? = null,
    val securityAnswer: String? = null,
    val deviceId: String? = null,
    val deviceLabel: String? = null
)
