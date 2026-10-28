package com.naniger.arzikina.data.remote.api

import com.naniger.arzikina.data.remote.RemoteConfig
import com.naniger.arzikina.data.remote.dto.DeleteAccountRequestDto
import com.naniger.arzikina.data.remote.dto.GoogleLoginRequestDto
import com.naniger.arzikina.data.remote.dto.GoogleLoginResponseDto
import com.naniger.arzikina.data.remote.dto.LinkGoogleAccountRequestDto
import com.naniger.arzikina.data.remote.dto.LoginRequestDto
import com.naniger.arzikina.data.remote.dto.LoginResponseDto
import com.naniger.arzikina.data.remote.dto.RegisterRequestDto
import com.naniger.arzikina.data.remote.dto.SetPasswordRequestDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

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
 * `login.php`/`register.php`/`logout.php` sont câblés ici (fondation réseau) — `api/sync/pull.php`/`push.php`
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

        val responseBody = okHttpClient.awaitBody(httpRequest)
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

        val responseBody = okHttpClient.awaitBody(httpRequest)
        return json.decodeFromString(LoginResponseDto.serializer(), responseBody)
    }

    /**
     * Ferme la session serveur de [rawToken] et coupe les notifications push des appareils qu'elle
     * avait enregistrés (voir `server/api/auth/logout.php`). Bearer posé explicitement : appelé
     * APRÈS l'effacement local de la session (voir `SyncAuthRepositoryImpl.logout`), le token n'est
     * donc plus lisible par `SyncAuthInterceptor`.
     */
    suspend fun logout(rawToken: String) {
        val httpRequest = Request.Builder()
            .url(RemoteConfig.BASE_URL + "api/auth/logout.php")
            .header("Authorization", "Bearer $rawToken")
            .post("".toRequestBody(jsonMediaType))
            .build()
        okHttpClient.awaitBody(httpRequest)
    }

    /**
     * Supprime DÉFINITIVEMENT le compte serveur de [rawToken] et toutes ses données (voir
     * `server/api/auth/delete_account.php` — un seul `DELETE FROM users` y cascade sur tout le
     * reste du schéma). [rawPassword] RE-DEMANDÉ dans le corps (voir [DeleteAccountRequestDto]) :
     * le serveur refuse avec un 401 (`HttpFailureException`) si le mot de passe ne correspond pas,
     * même avec un Bearer valide — voir [com.naniger.arzikina.data.repository.SyncAuthRepositoryImpl.deleteAccount]
     * pour l'interprétation de cette erreur.
     */
    suspend fun deleteAccount(rawToken: String, rawPassword: String) {
        val body = json.encodeToString(DeleteAccountRequestDto.serializer(), DeleteAccountRequestDto(rawPassword))
            .toRequestBody(jsonMediaType)
        val httpRequest = Request.Builder()
            .url(RemoteConfig.BASE_URL + "api/auth/delete_account.php")
            .header("Authorization", "Bearer $rawToken")
            .post(body)
            .build()
        okHttpClient.awaitBody(httpRequest)
    }

    /**
     * Authentification Google — voir `server/api/auth/google_login.php`. Réponse d'une forme
     * PROCHE mais pas identique à [login]/[register] (voir [GoogleLoginResponseDto]), donc un DTO
     * de réponse dédié plutôt qu'un partage forcé avec [LoginResponseDto].
     */
    suspend fun googleLogin(request: GoogleLoginRequestDto): GoogleLoginResponseDto {
        val body = json.encodeToString(GoogleLoginRequestDto.serializer(), request).toRequestBody(jsonMediaType)
        val httpRequest = Request.Builder()
            .url(RemoteConfig.BASE_URL + "api/auth/google_login.php")
            .post(body)
            .build()

        val responseBody = okHttpClient.awaitBody(httpRequest)
        return json.decodeFromString(GoogleLoginResponseDto.serializer(), responseBody)
    }

    /**
     * Lie un compte Google au compte Arzikina du Bearer fourni — voir
     * `server/api/auth/link_google_account.php` (cahier des charges, Cas C). Réponse vide en cas
     * de succès (`{"linked": true}`, jamais lue : un code HTTP 2xx suffit, voir `awaitBody`).
     */
    suspend fun linkGoogleAccount(rawToken: String, idToken: String) {
        val body = json.encodeToString(LinkGoogleAccountRequestDto.serializer(), LinkGoogleAccountRequestDto(idToken))
            .toRequestBody(jsonMediaType)
        val httpRequest = Request.Builder()
            .url(RemoteConfig.BASE_URL + "api/auth/link_google_account.php")
            .header("Authorization", "Bearer $rawToken")
            .post(body)
            .build()
        okHttpClient.awaitBody(httpRequest)
    }

    /**
     * Définit ou change le mot de passe du compte du Bearer fourni — voir `server/api/auth/set_password.php`
     * (preuve : mot de passe actuel OU jeton Google, voir [SetPasswordRequestDto]). Réponse non lue : un code
     * 2xx suffit ; une erreur remonte en [HttpFailureException] dont le message porte le corps brut.
     */
    suspend fun setPassword(rawToken: String, request: SetPasswordRequestDto) {
        val body = json.encodeToString(SetPasswordRequestDto.serializer(), request).toRequestBody(jsonMediaType)
        val httpRequest = Request.Builder()
            .url(RemoteConfig.BASE_URL + "api/auth/set_password.php")
            .header("Authorization", "Bearer $rawToken")
            .post(body)
            .build()
        okHttpClient.awaitBody(httpRequest)
    }
}

/** Réponse HTTP non-2xx — voir [SyncAuthRepositoryImpl][com.naniger.arzikina.data.repository.SyncAuthRepositoryImpl]
 *  pour son interprétation (401 -> identifiants invalides, reste -> erreur serveur). Remplace
 *  `retrofit2.HttpException`, plus disponible sans Retrofit. */
class HttpFailureException(val code: Int, message: String?) : IOException(message)
