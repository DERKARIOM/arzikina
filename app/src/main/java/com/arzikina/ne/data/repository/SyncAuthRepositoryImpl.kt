package com.arzikina.ne.data.repository

import android.content.Context
import android.provider.Settings
import com.arzikina.ne.data.remote.api.HttpFailureException
import com.arzikina.ne.data.remote.api.SyncAuthApi
import com.arzikina.ne.data.remote.dto.LoginRequestDto
import com.arzikina.ne.data.remote.dto.LoginResponseDto
import com.arzikina.ne.data.remote.dto.RegisterRequestDto
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.SyncAuthError
import com.arzikina.ne.domain.model.SyncAuthResult
import com.arzikina.ne.domain.model.SyncSession
import com.arzikina.ne.domain.repository.SyncAuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * Implémentation [SyncAuthRepository] (domaine). Le token brut ne transite JAMAIS en dehors de la
 * couche data (voir [SyncAuthStore], qui délègue lui-même à
 * [com.arzikina.ne.data.security.TokenCipher] pour le chiffrement) : ni loggé, ni exposé via
 * [SyncAuthRepository] au domaine/présentation.
 *
 * Se limite STRICTEMENT à la connexion au serveur (compte + session) — voir la KDoc de
 * [SyncAuthRepository] : ne déclenche PAS lui-même de synchronisation (pull/push) ni de résolution
 * de compte LOCAL. C'est [com.arzikina.ne.data.repository.UnifiedAuthRepositoryImpl] (couche
 * au-dessus) qui orchestre l'ensemble pour le login unifié — au moment où [login]/[register]
 * s'exécutent ici, le compte local correspondant n'existe pas forcément encore, donc tenter une
 * synchronisation à CET endroit serait prématuré (elle n'aurait pas encore de session locale
 * valide à laquelle rattacher les données reçues, voir `SessionManager`).
 *
 * Pas de rafraîchissement automatique du token à ce stade : un token expiré rend
 * [getActiveSession] silencieusement `null`, l'appelant devra alors demander une nouvelle
 * connexion — `api/auth/refresh.php` n'existe pas encore côté serveur (voir la KDoc de
 * `TOKEN_EXPIRY_SECONDS` dans `secrets.example.php`).
 */
class SyncAuthRepositoryImpl @Inject constructor(
    private val authApi: SyncAuthApi,
    @ApplicationContext private val context: Context,
    private val store: SyncAuthStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : SyncAuthRepository {

    override suspend fun login(
        identifier: String,
        rawPassword: String,
        deviceLabel: String?
    ): SyncAuthResult<SyncSession> = withContext(ioDispatcher) {
        runCatchingAuthCall {
            authApi.login(
                LoginRequestDto(
                    identifier = identifier,
                    password = rawPassword,
                    deviceId = androidDeviceId(),
                    deviceLabel = deviceLabel
                )
            )
        }
    }

    override suspend fun register(
        fullName: String,
        username: String,
        email: String,
        rawPassword: String,
        phoneNumber: String?,
        securityQuestion: String?,
        securityAnswer: String?,
        deviceLabel: String?
    ): SyncAuthResult<SyncSession> = withContext(ioDispatcher) {
        runCatchingAuthCall {
            authApi.register(
                RegisterRequestDto(
                    fullName = fullName,
                    username = username,
                    email = email,
                    phoneNumber = phoneNumber,
                    password = rawPassword,
                    securityQuestion = securityQuestion,
                    securityAnswer = securityAnswer,
                    deviceId = androidDeviceId(),
                    deviceLabel = deviceLabel
                )
            )
        }
    }

    /**
     * Factorise [login]/[register] (réponse de MÊME FORME, voir [LoginResponseDto]) : sauvegarde
     * de la session puis mise en correspondance des erreurs HTTP/réseau vers [SyncAuthError] — même
     * mapping pour les deux (401 → identifiants invalides pour [login], simplement improbable mais
     * inoffensif pour [register] ; 409 distingue [SyncAuthError.UsernameTaken]/[EmailTaken],
     * pertinent uniquement pour [register] mais sans risque de survenir pour [login]).
     */
    private suspend fun runCatchingAuthCall(call: suspend () -> LoginResponseDto): SyncAuthResult<SyncSession> =
        try {
            val response = call()
            store.saveSession(
                userId = response.userId,
                rawToken = response.token,
                expiresAt = response.expiresAt,
                fullName = response.fullName
            )
            SyncAuthResult.Success(
                SyncSession(serverUserId = response.userId, expiresAt = response.expiresAt, fullName = response.fullName)
            )
        } catch (e: HttpFailureException) {
            when (e.code) {
                401 -> SyncAuthResult.Failure(SyncAuthError.InvalidCredentials)
                409 -> SyncAuthResult.Failure(mapConflictError(e.message))
                else -> SyncAuthResult.Failure(SyncAuthError.ServerError(e.message))
            }
        } catch (e: IOException) {
            // Pas de réseau, timeout, hôte injoignable — voir OkHttpClient (délais dans NetworkModule).
            SyncAuthResult.Failure(SyncAuthError.NetworkUnavailable)
        } catch (e: Exception) {
            SyncAuthResult.Failure(SyncAuthError.Unknown(e))
        }

    /** [HttpFailureException.message] porte le corps HTTP brut (voir `SyncAuthApi.execute`) — sur
     *  un 409, ce corps est le JSON `{"error":"username_taken"|"email_taken",...}` de
     *  `register.php` ; recherche de texte volontairement simple plutôt qu'un vrai décodage JSON
     *  (pas de DTO d'erreur dédié à ce stade, un seul appelant). */
    private fun mapConflictError(rawBody: String?): SyncAuthError = when {
        rawBody?.contains("username_taken") == true -> SyncAuthError.UsernameTaken
        rawBody?.contains("email_taken") == true -> SyncAuthError.EmailTaken
        else -> SyncAuthError.ServerError(rawBody)
    }

    override suspend fun logout() {
        store.clear()
    }

    override suspend fun getActiveSession(): SyncSession? = store.getActiveSession()

    override fun observeActiveSession(): Flow<SyncSession?> = store.observeActiveSession()

    /** Identifiant d'appareil best-effort pour `auth_tokens.device_id` (voir `login.php`) — utile
     *  à une future gestion des appareils connectés, jamais utilisé comme secret. */
    private fun androidDeviceId(): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
}
