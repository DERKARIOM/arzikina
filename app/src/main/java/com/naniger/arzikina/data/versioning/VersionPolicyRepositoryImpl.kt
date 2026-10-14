package com.naniger.arzikina.data.versioning

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.naniger.arzikina.data.remote.RemoteConfig
import com.naniger.arzikina.data.remote.api.awaitBody
import com.naniger.arzikina.di.AppUpdateDataStore
import com.naniger.arzikina.di.IoDispatcher
import com.naniger.arzikina.domain.versioning.AppVersionPolicy
import com.naniger.arzikina.domain.versioning.VersionPolicyRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [VersionPolicyRepository] adossé à l'API PHP. Le client OkHttp est le client de base
 * (`AppVersionInterceptor` y pose déjà `X-App-Version-Code`) ; la route est publique, aucun jeton.
 *
 * Cache : le corps JSON brut de la dernière réponse valide, dans le DataStore des mises à jour
 * (`@AppUpdateDataStore`, valable pour l'appareil et non pour un profil). Stocker le brut plutôt
 * qu'un second format évite un schéma de plus à migrer : il est relu avec le même DTO.
 *
 * Tolérant : toute erreur (réseau, HTTP, JSON, DataStore) donne `null`, jamais une exception — la
 * politique ne doit jamais faire planter l'app.
 */
@Singleton
internal class VersionPolicyRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    @AppUpdateDataStore private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : VersionPolicyRepository {

    override suspend fun cachedPolicy(): AppVersionPolicy? = withContext(ioDispatcher) {
        runCatching {
            val raw = dataStore.data.firstOrNull()?.get(KEY_POLICY_JSON) ?: return@runCatching null
            parse(raw)
        }.getOrNull()
    }

    override suspend fun fetchPolicy(): AppVersionPolicy? = withContext(ioDispatcher) {
        runCatching {
            val request = Request.Builder()
                .url(RemoteConfig.BASE_URL + "api/app/version_policy.php")
                .get()
                .build()
            val raw = okHttpClient.awaitBody(request)
            val policy = parse(raw) ?: return@runCatching null
            dataStore.edit { it[KEY_POLICY_JSON] = raw }
            policy
        }.getOrNull()
    }

    private fun parse(raw: String): AppVersionPolicy? {
        val dto = runCatching { json.decodeFromString(VersionPolicyDto.serializer(), raw) }.getOrNull()
            ?: return null
        return AppVersionPolicy(
            minVersionCode = dto.minVersionCode.coerceAtLeast(0),
            forceUpdate = dto.forceUpdate,
            minVersionName = dto.minVersionName?.takeIf(String::isNotBlank),
            // Défense en profondeur : seul un lien HTTPS est jamais ouvert (le serveur le valide
            // déjà, mais une réponse altérée ne doit pas pouvoir ouvrir autre chose).
            updateUrl = dto.updateUrl.takeIf { it.startsWith("https://") } ?: DEFAULT_UPDATE_URL,
            latestVersionName = dto.latestVersionName?.takeIf(String::isNotBlank),
            releaseNotes = dto.releaseNotes?.takeIf(String::isNotBlank),
            maintenanceEnabled = dto.maintenance.enabled,
            maintenanceMessage = dto.maintenance.message?.takeIf(String::isNotBlank)
        )
    }

    private companion object {
        val KEY_POLICY_JSON = stringPreferencesKey("version_policy_json")
        const val DEFAULT_UPDATE_URL = "https://play.google.com/store/apps/details?id=com.naniger.arzikina"
    }
}
