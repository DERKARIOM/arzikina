package com.naniger.arzikina.data.remote.api

import com.naniger.arzikina.data.remote.RemoteConfig
import com.naniger.arzikina.data.remote.dto.LoginRequestDto
import com.naniger.arzikina.data.remote.dto.LoginResponseDto
import com.naniger.arzikina.data.remote.dto.RegisterRequestDto
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
 * Client HTTP pour les routes `server/api/auth/` — OkHttp direct, SANS Retrofit. Voir
 * docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md, diagnostic Sync Engine : que ce soit Retrofit 3.0.0 ou
 * 2.11.0, un simple `@Provides fun provide(retrofit: Retrofit): X = retrofit.create(X::class.java)`
 * suffit SEUL à faire échouer KSP/Dagger dans ce toolchain — confirmé par bisection systématique
 * (dépendances seules OK, domaine seul OK, un seul `@Provides` Retrofit minimal suffit à
 * reproduire l'erreur). Cette classe CONCRÈTE, `@Inject`-constructible directement (même schéma
 * que [com.naniger.arzikina.data.repository.SyncAuthStore], déjà éprouvé sans problème dans ce même
 * diagnostic), contourne entièrement le mécanisme `retrofit.create()` mis en cause.
 *
 * `login.php`/`register.php` sont câblés ici (fondation réseau) — `api/sync/pull.php`/`push.php`
 * suivront le même schéma (une méthode suspend de plus ici, ou une classe dédiée si le nombre
 * d'endpoints grossit).
 */
@Singleton
class SyncAuthApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun login(request: LoginRequestDto): LoginResponseDto {
        val body = json.encodeToString(LoginRequestDto.serializer(), request).toRequestBody(jsonMediaType)
        val httpRequest = Request.Builder()
            .url(RemoteConfig.BASE_URL + "api/auth/login.php")
            .post(body)
            .build()

        val responseBody = execute(httpRequest)
        return json.decodeFromString(LoginResponseDto.serializer(), responseBody)
    }

    /** Voir `server/api/auth/register.php` — réponse de MÊME FORME que [login] (voir la KDoc de
     *  [LoginResponseDto]), réutilisée telle quelle plutôt qu'un DTO dupliqué. */
    suspend fun register(request: RegisterRequestDto): LoginResponseDto {
        val body = json.encodeToString(RegisterRequestDto.serializer(), request).toRequestBody(jsonMediaType)
        val httpRequest = Request.Builder()
            .url(RemoteConfig.BASE_URL + "api/auth/register.php")
            .post(body)
            .build()

        val responseBody = execute(httpRequest)
        return json.decodeFromString(LoginResponseDto.serializer(), responseBody)
    }

    /** Pont callback OkHttp -> coroutine ; annule l'appel HTTP si la coroutine est annulée. */
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
                        // Corps JSON de la réponse (ex. {"error":"username_taken",...}, voir
                        // utils/json_response.php), PAS `it.message` (simple libellé HTTP générique
                        // du type "Conflict") — SyncAuthRepositoryImpl.mapConflictError() a besoin du
                        // code d'erreur métier réel pour distinguer username_taken/email_taken.
                        continuation.resumeWithException(HttpFailureException(it.code, it.body?.string()))
                        return
                    }
                    continuation.resume(it.body?.string().orEmpty())
                }
            }
        })
    }
}

/** Réponse HTTP non-2xx — voir [SyncAuthRepositoryImpl][com.naniger.arzikina.data.repository.SyncAuthRepositoryImpl]
 *  pour son interprétation (401 -> identifiants invalides, reste -> erreur serveur). Remplace
 *  `retrofit2.HttpException`, plus disponible sans Retrofit. */
class HttpFailureException(val code: Int, message: String?) : IOException(message)
