package com.naniger.arzikina.data.repository

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.naniger.arzikina.data.push.PushStore
import com.naniger.arzikina.data.push.PushTokenSource
import com.naniger.arzikina.data.remote.api.DevicesApi
import com.naniger.arzikina.data.remote.api.HttpFailureException
import com.naniger.arzikina.data.remote.dto.DeviceRegistrationRequestDto
import com.naniger.arzikina.domain.model.PushRegistrationOutcome
import com.naniger.arzikina.domain.repository.PushRegistrationRepository
import com.naniger.arzikina.work.PushRegistrationScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation [PushRegistrationRepository] — voir sa KDoc pour le contrat.
 *
 * Évite les appels inutiles : une « signature » (compte, session, token, installation, version,
 * langue) du dernier envoi réussi est conservée ; tant qu'elle est identique et date de moins de
 * [REFRESH_INTERVAL_MILLIS], rien n'est renvoyé. La session fait partie de la signature : une
 * reconnexion au même compte ouvre une nouvelle session serveur, l'appareil doit y être rattaché.
 * Le rafraîchissement hebdomadaire met à jour `last_seen_at` côté serveur (nettoyage futur des
 * appareils inactifs).
 */
@Singleton
class PushRegistrationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncAuthStore: SyncAuthStore,
    private val pushStore: PushStore,
    private val tokenSource: PushTokenSource,
    private val devicesApi: DevicesApi
) : PushRegistrationRepository {

    override suspend fun onNewToken(token: String) {
        if (token.isBlank()) return
        pushStore.saveToken(token)
        requestRegistration()
    }

    override fun requestRegistration() {
        PushRegistrationScheduler.enqueue(context)
    }

    override suspend fun registerNow(): PushRegistrationOutcome {
        val session = syncAuthStore.getActiveSession() ?: return PushRegistrationOutcome.NotSignedIn

        // Token frais de Firebase en priorité (couvre une rotation dont `onNewToken` n'aurait pas
        // été livré), sinon le dernier connu.
        val freshToken = tokenSource.currentToken()
        if (freshToken != null && freshToken != pushStore.getToken()) pushStore.saveToken(freshToken)
        val token = freshToken ?: pushStore.getToken() ?: return PushRegistrationOutcome.Retry

        val request = DeviceRegistrationRequestDto(
            token = token,
            installationId = pushStore.getOrCreateInstallationId(),
            platform = PLATFORM,
            appVersion = appVersionName(),
            locale = appLocaleTag()
        )
        val signature = signatureOf(session.serverUserId, session.expiresAt.toString(), request.toString())
        val now = System.currentTimeMillis()
        val last = pushStore.getLastRegistration()
        if (last != null && last.signature == signature && now - last.registeredAtMillis < REFRESH_INTERVAL_MILLIS) {
            return PushRegistrationOutcome.Registered
        }

        return try {
            devicesApi.register(request)
            pushStore.saveLastRegistration(PushStore.LastRegistration(signature, now))
            PushRegistrationOutcome.Registered
        } catch (e: HttpFailureException) {
            // Avant IOException : HttpFailureException en hérite.
            when (e.code) {
                401 -> PushRegistrationOutcome.NotSignedIn // session révoquée/expirée côté serveur
                408, 429 -> PushRegistrationOutcome.Retry
                in 500..599 -> PushRegistrationOutcome.Retry
                else -> PushRegistrationOutcome.Rejected
            }
        } catch (e: IOException) {
            PushRegistrationOutcome.Retry
        }
    }

    override suspend fun onSignedOut() {
        PushRegistrationScheduler.cancel(context)
        pushStore.clearLastRegistration()
    }

    private fun appVersionName(): String? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()?.take(MAX_VERSION_LENGTH)

    /** Langue choisie dans l'application (Paramètres), sinon celle du système. */
    private fun appLocaleTag(): String {
        val locale = AppCompatDelegate.getApplicationLocales()[0] ?: context.resources.configuration.locales[0]
        return locale?.language?.ifBlank { null } ?: DEFAULT_LANGUAGE
    }

    /** Empreinte SHA-256 : le token n'est pas recopié en clair une seconde fois sur le disque. */
    private fun signatureOf(vararg parts: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(parts.joinToString("\u0000").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val PLATFORM = "android"
        const val DEFAULT_LANGUAGE = "fr"
        const val MAX_VERSION_LENGTH = 32
        const val REFRESH_INTERVAL_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
