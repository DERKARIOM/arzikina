package com.naniger.arzikina.data.push

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Source du token FCM courant. Interface pour isoler Firebase du dépôt
 * (`PushRegistrationRepositoryImpl` reste testable sans Firebase).
 */
interface PushTokenSource {
    /** Token courant, ou `null` s'il n'a pas pu être obtenu (pas de réseau, services Google absents). */
    suspend fun currentToken(): String?
}

/**
 * Implémentation Firebase. Pont `Task` → coroutine écrit à la main : évite d'ajouter la
 * dépendance `kotlinx-coroutines-play-services` pour un seul appel.
 */
class FirebasePushTokenSource @Inject constructor() : PushTokenSource {

    override suspend fun currentToken(): String? = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (continuation.isActive) {
                continuation.resume(if (task.isSuccessful) task.result?.takeIf { it.isNotBlank() } else null)
            }
        }
    }
}
