package com.arzikina.ne.data.remote.api

import com.arzikina.ne.data.remote.RemoteConfig
import com.arzikina.ne.data.remote.SyncHttpClient
import com.arzikina.ne.data.remote.dto.SyncPushRequestDto
import com.arzikina.ne.data.remote.dto.SyncPushResponseDto
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Client HTTP pour les routes `server/api/sync/` — même schéma qu'[SyncAuthApi] (OkHttp direct,
 * SANS Retrofit ; voir sa KDoc pour le diagnostic complet), mais sur le client qualifié
 * [SyncHttpClient] : chaque appel de cette classe part avec l'en-tête `Authorization` déjà posé
 * (voir `SyncAuthInterceptor`), contrairement à [SyncAuthApi] (`login.php`, pas encore de token).
 *
 * Seul `push.php` est câblé à cette étape (voir `SyncEngineImpl`) — `pull.php` suivra le même
 * schéma (une méthode suspend de plus ici) lorsque la réception des changements distants sera
 * construite.
 */
@Singleton
class SyncApi @Inject constructor(
    @SyncHttpClient private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun push(request: SyncPushRequestDto): SyncPushResponseDto {
        val body = json.encodeToString(SyncPushRequestDto.serializer(), request).toRequestBody(jsonMediaType)
        val httpRequest = Request.Builder()
            .url(RemoteConfig.BASE_URL + "api/sync/push.php")
            .post(body)
            .build()

        val responseBody = execute(httpRequest)
        return json.decodeFromString(SyncPushResponseDto.serializer(), responseBody)
    }

    /** Pont callback OkHttp -> coroutine ; annule l'appel HTTP si la coroutine est annulée. Même
     *  implémentation que [SyncAuthApi.execute] — pas encore extraite en commun pour deux
     *  occurrences (voir le même raisonnement que `push.php` côté serveur sur l'abstraction
     *  prématurée), à revoir si une troisième classe HTTP apparaît. */
    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = okHttpClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        continuation.resumeWithException(HttpFailureException(it.code, it.message))
                        return
                    }
                    continuation.resume(it.body?.string().orEmpty())
                }
            }
        })
    }
}
