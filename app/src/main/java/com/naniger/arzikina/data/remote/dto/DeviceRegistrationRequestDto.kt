package com.naniger.arzikina.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Corps de `POST /api/devices/register.php` — voir ce fichier pour les règles de validation
 * serveur. Aucun identifiant matériel : [installationId] est un UUID aléatoire propre à
 * l'installation (voir `PushStore`). Un champ `null` est envoyé tel quel : le serveur l'enregistre à NULL.
 */
@Serializable
data class DeviceRegistrationRequestDto(
    val token: String,
    val installationId: String,
    /** Sans valeur par défaut : le [kotlinx.serialization.json.Json] partagé n'encode pas les
     *  valeurs par défaut, le champ serait omis et refusé (400) par le serveur. */
    val platform: String,
    val appVersion: String?,
    val locale: String?
)
