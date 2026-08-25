package com.arzikina.ne.data.repository

import android.content.Context
import android.provider.Settings
import com.arzikina.ne.data.remote.api.HttpFailureException
import com.arzikina.ne.data.remote.api.SyncAuthApi
import com.arzikina.ne.data.remote.dto.LoginRequestDto
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.SyncAuthError
import com.arzikina.ne.domain.model.SyncAuthResult
import com.arzikina.ne.domain.model.SyncSession
import com.arzikina.ne.domain.repository.SyncAuthRepository
import com.arzikina.ne.domain.repository.SyncEngine
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
 * Pas de rafraîchissement automatique du token à ce stade : un token expiré rend
 * [getActiveSession] silencieusement `null`, l'appelant (futur Sync Engine) devra alors demander
 * une nouvelle connexion — `api/auth/refresh.php` n'existe pas encore côté serveur (voir la KDoc de
 * `TOKEN_EXPIRY_SECONDS` dans `secrets.example.php`).
 */
class SyncAuthRepositoryImpl @Inject constructor(
    private val authApi: SyncAuthApi,
    @ApplicationContext private val context: Context,
    private val store: SyncAuthStore,
    private val syncEngine: SyncEngine,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : SyncAuthRepository {

    override suspend fun login(
        identifier: String,
        rawPassword: String,
        deviceLabel: String?
    ): SyncAuthResult<SyncSession> = withContext(ioDispatcher) {
        try {
            val response = authApi.login(
                LoginRequestDto(
                    identifier = identifier,
                    password = rawPassword,
                    deviceId = androidDeviceId(),
                    deviceLabel = deviceLabel
                )
            )

            store.saveSession(
                userId = response.userId,
                rawToken = response.token,
                expiresAt = response.expiresAt
            )

            // Best-effort via runCatching : la session est déjà valide et sauvegardée à ce stade
            // (voir juste au-dessus) — une exception ici ne doit JAMAIS remonter jusqu'aux blocs
            // catch ci-dessous ni transformer ce login en échec, l'utilisateur EST connecté. Voir
            // SyncEngine.enqueueUnsyncedLocalData pour le raisonnement complet (données locales
            // créées en dehors des repositories câblés sur sync_queue, ex. catégories par défaut).
            runCatching { syncEngine.enqueueUnsyncedLocalData() }

            SyncAuthResult.Success(SyncSession(serverUserId = response.userId, expiresAt = response.expiresAt))
        } catch (e: HttpFailureException) {
            if (e.code == 401) {
                SyncAuthResult.Failure(SyncAuthError.InvalidCredentials)
            } else {
                SyncAuthResult.Failure(SyncAuthError.ServerError(e.message))
            }
        } catch (e: IOException) {
            // Pas de réseau, timeout, hôte injoignable — voir OkHttpClient (délais dans NetworkModule).
            SyncAuthResult.Failure(SyncAuthError.NetworkUnavailable)
        } catch (e: Exception) {
            SyncAuthResult.Failure(SyncAuthError.Unknown(e))
        }
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
