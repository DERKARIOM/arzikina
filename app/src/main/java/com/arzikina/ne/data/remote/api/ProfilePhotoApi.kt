package com.arzikina.ne.data.remote.api

import com.arzikina.ne.data.remote.RemoteConfig
import com.arzikina.ne.data.remote.SyncHttpClient
import com.arzikina.ne.data.remote.dto.ProfilePhotoSyncResponseDto
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
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
 * Client HTTP pour les routes `server/api/profile/` (photo de profil) — même schéma qu'[SyncApi]
 * (OkHttp direct, SANS Retrofit ; voir la KDoc de [SyncAuthApi] pour le diagnostic complet).
 *
 * Deux clients injectés, volontairement distincts :
 * - [syncHttpClient] ([SyncHttpClient], `Authorization` déjà posé par `SyncAuthInterceptor`) pour
 *   les 3 endpoints authentifiés (`upload_photo.php`/`delete_photo.php`/`get.php`).
 * - [plainHttpClient] (client de base, SANS interceptor) pour [downloadPhoto] : un simple fichier
 *   statique servi directement par le serveur web (voir `server/avatars/`), jamais derrière
 *   `requireAuthenticatedUser` côté PHP — inutile d'y joindre un token.
 */
@Singleton
class ProfilePhotoApi @Inject constructor(
    @SyncHttpClient private val syncHttpClient: OkHttpClient,
    private val plainHttpClient: OkHttpClient,
    private val json: Json
) {

    /** [jpegBytes] : déjà recadrée/optimisée par l'appelant (voir `ProfileFragment.launchCrop`) —
     *  cette classe ne fait AUCUN traitement d'image, uniquement le transport. */
    suspend fun uploadPhoto(jpegBytes: ByteArray): ProfilePhotoSyncResponseDto {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("photo", "profile_photo.jpg", jpegBytes.toRequestBody(JPEG_MEDIA_TYPE))
            .build()
        val httpRequest = Request.Builder()
            .url(RemoteConfig.BASE_URL + "api/profile/upload_photo.php")
            .post(body)
            .build()

        val responseBody = execute(syncHttpClient, httpRequest)
        return json.decodeFromString(ProfilePhotoSyncResponseDto.serializer(), responseBody)
    }

    /** Corps vide : `delete_photo.php` n'a besoin que de l'en-tête `Authorization` (voir sa KDoc). */
    suspend fun deletePhoto(): ProfilePhotoSyncResponseDto {
        val httpRequest = Request.Builder()
            .url(RemoteConfig.BASE_URL + "api/profile/delete_photo.php")
            .post(ByteArray(0).toRequestBody())
            .build()

        val responseBody = execute(syncHttpClient, httpRequest)
        return json.decodeFromString(ProfilePhotoSyncResponseDto.serializer(), responseBody)
    }

    /** Lecture seule — voir `server/api/profile/get.php`. Appelé par
     *  [com.arzikina.ne.data.repository.ProfilePhotoRepositoryImpl.syncWithServer] pour détecter une
     *  photo plus récente venue d'un autre appareil. */
    suspend fun getPhoto(): ProfilePhotoSyncResponseDto {
        val httpRequest = Request.Builder()
            .url(RemoteConfig.BASE_URL + "api/profile/get.php")
            .get()
            .build()

        val responseBody = execute(syncHttpClient, httpRequest)
        return json.decodeFromString(ProfilePhotoSyncResponseDto.serializer(), responseBody)
    }

    /** [relativePath] : voir [ProfilePhotoSyncResponseDto.photoPath] — jamais appelé avec une valeur
     *  `null` (l'appelant filtre déjà, voir `ProfilePhotoRepositoryImpl.syncWithServer`). */
    suspend fun downloadPhoto(relativePath: String): ByteArray = suspendCancellableCoroutine { continuation ->
        val call = plainHttpClient.newCall(
            Request.Builder().url(RemoteConfig.BASE_URL + relativePath).get().build()
        )
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
                    continuation.resume(it.body?.bytes() ?: ByteArray(0))
                }
            }
        })
    }

    /** Pont callback OkHttp -> coroutine ; annule l'appel HTTP si la coroutine est annulée — même
     *  implémentation que [SyncApi.execute]/[SyncAuthApi.execute]. */
    private suspend fun execute(client: OkHttpClient, request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        continuation.resumeWithException(HttpFailureException(it.code, it.body?.string()))
                        return
                    }
                    continuation.resume(it.body?.string().orEmpty())
                }
            }
        })
    }

    private companion object {
        val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
