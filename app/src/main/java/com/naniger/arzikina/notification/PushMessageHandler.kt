package com.naniger.arzikina.notification

import com.naniger.arzikina.data.push.PushPayloadParser
import com.naniger.arzikina.data.push.PushRecipientVerifier
import javax.inject.Inject

/**
 * Traitement d'un message FCM reçu : validation du format, contrôle du destinataire, affichage.
 * Séparé de `push/ArzikinaMessagingService` pour que le service reste un simple point d'entrée
 * Android et que la décision « afficher ou ignorer » soit lisible en un seul endroit.
 *
 * Un message ignoré ne laisse aucune trace visible : format inconnu (application plus ancienne que
 * le serveur), autre compte, ou personne de connecté.
 */
class PushMessageHandler @Inject constructor(
    private val recipientVerifier: PushRecipientVerifier,
    private val notifier: PushNotifier
) {
    /** @return `true` si une notification a été affichée. */
    suspend fun handle(data: Map<String, String>): Boolean {
        val message = PushPayloadParser.parse(data) ?: return false
        if (!recipientVerifier.isCurrentRecipient(message.recipientServerUserId)) return false
        notifier.show(message)
        return true
    }
}
