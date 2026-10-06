package com.naniger.arzikina.data.remote.api

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Pont callback OkHttp → coroutine, partagé par les clients HTTP de `data/remote/api/`.
 *
 * - Annule l'appel HTTP si la coroutine est annulée.
 * - Réponse non-2xx → [HttpFailureException] portant le CORPS de la réponse (le JSON
 *   `{"error":"...","message":"..."}` de `server/api/utils/json_response.php`), le libellé HTTP
 *   n'étant utilisé qu'à défaut de corps : l'appelant a besoin du code d'erreur métier.
 *
 * Extrait de [SyncAuthApi] à l'arrivée d'un nouveau client ([DevicesApi]), comme annoncé par la
 * KDoc de `SyncApi.execute`. `SyncApi` et `ProfilePhotoApi` gardent pour l'instant leur propre copie
 * (messages d'erreur légèrement différents, consommés ailleurs) : migration à faire séparément.
 */
internal suspend fun OkHttpClient.awaitBody(request: Request): String = suspendCancellableCoroutine { continuation ->
    val call = newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                val body = it.body?.string()
                if (!it.isSuccessful) {
                    continuation.resumeWithException(HttpFailureException(it.code, body?.takeIf(String::isNotBlank) ?: it.message))
                    return
                }
                continuation.resume(body.orEmpty())
            }
        }
    })
}
