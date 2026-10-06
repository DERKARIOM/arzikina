package com.naniger.arzikina.data.remote.api

import com.naniger.arzikina.data.remote.RemoteConfig
import com.naniger.arzikina.data.remote.SyncHttpClient
import com.naniger.arzikina.data.remote.dto.DeviceRegistrationRequestDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client HTTP pour `server/api/devices/` (notifications push). Même schéma que [SyncApi] : OkHttp
 * direct sans Retrofit, client qualifié [SyncHttpClient] qui pose l'en-tête `Authorization` de la
 * session serveur courante (voir `SyncAuthInterceptor`). Le serveur rattache l'appareil au compte
 * ET à la session de ce Bearer : aucun identifiant de compte n'est envoyé dans le corps.
 */
@Singleton
class DevicesApi @Inject constructor(
    @SyncHttpClient private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** @throws HttpFailureException réponse non-2xx (401 = session invalide, 400 = requête refusée) */
    suspend fun register(request: DeviceRegistrationRequestDto) {
        val body = json.encodeToString(DeviceRegistrationRequestDto.serializer(), request).toRequestBody(jsonMediaType)
        okHttpClient.awaitBody(
            Request.Builder()
                .url(RemoteConfig.BASE_URL + "api/devices/register.php")
                .post(body)
                .build()
        )
    }
}
