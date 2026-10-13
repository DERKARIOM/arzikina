package com.naniger.arzikina.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Réponse de `POST /api/auth/google_login.php` — même forme que [LoginResponseDto] (voir sa doc),
 * ENRICHIE de deux champs que `login.php`/`register.php` n'ont pas besoin de renvoyer (l'appelant
 * connaît déjà l'e-mail saisi, et sait déjà s'il appelle login ou register) :
 *
 * [email] : adresse e-mail Google déjà VÉRIFIÉE par le serveur (voir `google_token_verifier.php`)
 * — le client n'a jamais à relire le jeton Google lui-même pour l'obtenir.
 *
 * [isNewAccount] : `true` si ce compte Arzikina vient d'être créé par cet appel (Cas B du cahier
 * des charges), `false` s'il existait déjà (Cas A) — nécessaire côté
 * [com.naniger.arzikina.data.repository.UnifiedAuthRepositoryImpl] pour décider si les données par
 * défaut doivent être semées pour le nouvel utilisateur local (même logique que `register()`,
 * jamais pour une connexion à un compte déjà existant).
 */
@Serializable
data class GoogleLoginResponseDto(
    val token: String,
    val userId: String,
    val expiresAt: Long,
    val fullName: String,
    val email: String,
    val isNewAccount: Boolean
)
