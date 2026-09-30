package com.naniger.arzikina.data.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.naniger.arzikina.di.AppUpdateDataStore
import com.naniger.arzikina.di.IoDispatcher
import com.naniger.arzikina.domain.repository.InAppUpdatePromptStore
import com.naniger.arzikina.domain.update.UpdatePromptDismissal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [InAppUpdatePromptStore] adossé à un DataStore dédié (`@AppUpdateDataStore`, voir
 * `di/DataStoreModule.kt`) : cette donnée n'appartient ni aux préférences d'affichage ni à la
 * session, et ne concerne aucun profil utilisateur (elle vaut pour l'appareil).
 *
 * Tolérant : une lecture/écriture impossible ne doit jamais empêcher d'utiliser l'app — au pire,
 * la proposition de mise à jour réapparaît plus tôt que prévu.
 */
@Singleton
class InAppUpdatePromptStoreImpl @Inject constructor(
    @AppUpdateDataStore private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : InAppUpdatePromptStore {

    override suspend fun lastDismissal(): UpdatePromptDismissal? = withContext(ioDispatcher) {
        runCatching {
            val prefs = dataStore.data.firstOrNull() ?: return@runCatching null
            val versionCode = prefs[KEY_DISMISSED_VERSION_CODE] ?: return@runCatching null
            val dismissedAt = prefs[KEY_DISMISSED_AT_MILLIS] ?: return@runCatching null
            UpdatePromptDismissal(versionCode, dismissedAt)
        }.getOrNull()
    }

    override suspend fun recordDismissal(dismissal: UpdatePromptDismissal) {
        withContext(ioDispatcher) {
            runCatching {
                dataStore.edit { prefs ->
                    prefs[KEY_DISMISSED_VERSION_CODE] = dismissal.versionCode
                    prefs[KEY_DISMISSED_AT_MILLIS] = dismissal.dismissedAtMillis
                }
            }
        }
    }

    private companion object {
        val KEY_DISMISSED_VERSION_CODE = intPreferencesKey("in_app_update_dismissed_version_code")
        val KEY_DISMISSED_AT_MILLIS = longPreferencesKey("in_app_update_dismissed_at_millis")
    }
}
