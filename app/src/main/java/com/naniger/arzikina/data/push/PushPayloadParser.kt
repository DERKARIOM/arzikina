package com.naniger.arzikina.data.push

import com.naniger.arzikina.domain.model.PushMessage
import com.naniger.arzikina.domain.model.PushMessageType

/**
 * Convertit le bloc `data` d'un message FCM en [PushMessage], ou `null` si le message doit être
 * ignoré. Kotlin pur, sans Android ni Firebase : testable en JVM (voir `PushPayloadParserTest`).
 *
 * Rejeté (jamais d'exception, jamais de plantage du service de réception) :
 * - version de format inconnue (`v` différent de [SUPPORTED_VERSION]) ;
 * - type inconnu de cette version de l'application ;
 * - `uid` ou `event_id` absent ou qui n'est pas un UUID ;
 * - entité absente, mal formée ou d'un autre type que celui attendu par le type de message ;
 * - entité présente alors que le type n'en porte pas.
 */
object PushPayloadParser {

    const val SUPPORTED_VERSION = "1"

    private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun parse(data: Map<String, String>): PushMessage? {
        if (data["v"] != SUPPORTED_VERSION) return null
        val type = data["type"]?.let(PushMessageType::fromWireName) ?: return null
        val uid = data["uid"]?.takeIf(::isUuid) ?: return null
        val eventId = data["event_id"]?.takeIf(::isUuid) ?: return null
        val sentAt = data["sent_at"]?.toLongOrNull() ?: 0L

        val entityType = data["entity_type"]
        val entityId = data["entity_id"]
        val expectedEntityType = type.entityType
        if (expectedEntityType == null) {
            if (entityType != null || entityId != null) return null
        } else if (entityType != expectedEntityType || entityId == null || !isUuid(entityId)) {
            return null
        }

        return PushMessage(
            type = type,
            recipientServerUserId = uid.lowercase(),
            entityServerId = entityId?.lowercase(),
            eventId = eventId.lowercase(),
            sentAtMillis = sentAt
        )
    }

    private fun isUuid(value: String): Boolean = UUID_REGEX.matches(value)
}
