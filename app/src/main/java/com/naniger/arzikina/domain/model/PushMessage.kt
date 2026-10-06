package com.naniger.arzikina.domain.model

/**
 * Notification push reçue du serveur Arzikina, déjà validée (voir
 * `data/push/PushPayloadParser`). Reflète le format « data » v1 envoyé par
 * `server/api/push/PushPayload.php` : aucun texte, aucun montant, seulement de quoi retrouver
 * l'information dans la base locale et composer la notification dans la langue de l'application.
 *
 * @property recipientServerUserId compte serveur destinataire (`uid`) : l'application ignore le
 *   message si ce n'est pas le compte actuellement connecté (voir `PushRecipientVerifier`).
 * @property entityServerId UUID serveur (`syncId`) de l'entité concernée, jamais un identifiant
 *   local Room ; `null` pour les types sans entité.
 * @property eventId identifiant unique de l'événement côté serveur.
 */
data class PushMessage(
    val type: PushMessageType,
    val recipientServerUserId: String,
    val entityServerId: String?,
    val eventId: String,
    val sentAtMillis: Long
)

/**
 * Types connus de cette version de l'application. Un type que l'application ne connaît pas (ajouté
 * côté serveur pour une version plus récente) n'est jamais converti en [PushMessage] : il est ignoré
 * sans erreur. Ajouter un type = une valeur ici, son [wireName] identique à la constante PHP, puis
 * son affichage dans `PushNotifier`.
 *
 * @property entityType type d'entité attendu dans le message (`null` = aucune).
 */
enum class PushMessageType(val wireName: String, val entityType: String?) {
    SYSTEM_MESSAGE("SYSTEM_MESSAGE", null),
    LOAN_DUE("LOAN_DUE", "loan"),
    SECURITY_ALERT("SECURITY_ALERT", null);

    companion object {
        fun fromWireName(value: String): PushMessageType? = entries.firstOrNull { it.wireName == value }
    }
}
