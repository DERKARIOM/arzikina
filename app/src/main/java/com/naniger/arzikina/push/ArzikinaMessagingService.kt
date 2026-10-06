package com.naniger.arzikina.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.naniger.arzikina.di.ApplicationScope
import com.naniger.arzikina.domain.repository.PushRegistrationRepository
import com.naniger.arzikina.notification.PushMessageHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Point d'entrée Firebase Cloud Messaging. Volontairement mince : délègue tout à
 * [PushRegistrationRepository] (token) et [PushMessageHandler] (messages).
 *
 * Le serveur n'envoie que des messages « data » : [onMessageReceived] est appelé que l'application
 * soit au premier plan, en arrière-plan ou fermée, et c'est toujours l'application qui construit la
 * notification (aucun doublon possible avec un affichage automatique de Firebase).
 *
 * [onMessageReceived] s'exécute déjà hors du thread principal et doit se terminer en quelques
 * secondes : traitement bloquant borné par [HANDLE_TIMEOUT_MILLIS] (lectures locales uniquement).
 * Le message brut n'est jamais journalisé.
 */
@AndroidEntryPoint
class ArzikinaMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushRegistrationRepository: PushRegistrationRepository
    @Inject lateinit var pushMessageHandler: PushMessageHandler
    // `@field:` : place le qualificatif sur le champ Java vu par Dagger (injection par champ).
    @Inject @field:ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onNewToken(token: String) {
        // Portée application : l'enregistrement local du token ne doit pas être interrompu par
        // l'arrêt du service ; l'envoi au serveur passe ensuite par WorkManager.
        applicationScope.launch { pushRegistrationRepository.onNewToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data.isEmpty()) return
        runBlocking {
            withTimeoutOrNull(HANDLE_TIMEOUT_MILLIS) { runCatching { pushMessageHandler.handle(data) } }
        }
    }

    private companion object {
        const val HANDLE_TIMEOUT_MILLIS = 5_000L
    }
}
