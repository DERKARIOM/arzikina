package com.naniger.arzikina.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Corps de `POST /api/auth/link_google_account.php` (authentifié par Bearer, voir
 * `SyncAuthApi.linkGoogleAccount`) — voir `server/api/auth/link_google_account.php` pour le
 * contrat exact. Cahier des charges, Cas C : lie un compte Google à un compte Arzikina EXISTANT,
 * déjà prouvé par le Bearer (connexion classique préalable) — jamais une fusion automatique sur la
 * seule base de l'e-mail.
 */
@Serializable
data class LinkGoogleAccountRequestDto(
    val idToken: String
)
