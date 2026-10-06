package com.naniger.arzikina.data.push

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.naniger.arzikina.di.IoDispatcher
import com.naniger.arzikina.di.PushDataStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * État local des notifications push, dans un DataStore DÉDIÉ (`arzikina_push`, voir
 * `di/DataStoreModule`) : rien ici n'appartient à un compte utilisateur.
 *
 * - [getOrCreateInstallationId] : UUID aléatoire créé à la première demande, propre à CETTE
 *   installation. Ce n'est PAS `ANDROID_ID` ni un identifiant matériel : il disparaît avec une
 *   désinstallation ou un effacement des données (aucun suivi possible au-delà de l'application).
 * - token FCM : conservé tant qu'aucun compte n'est connecté, envoyé après la connexion.
 * - « signature » du dernier enregistrement réussi : évite de renvoyer la même chose au serveur à
 *   chaque démarrage (voir `PushRegistrationRepositoryImpl`).
 *
 * Aucune donnée sensible : le token FCM n'est utilisable qu'avec la clé privée du serveur.
 */
@Singleton
class PushStore @Inject constructor(
    @PushDataStore private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private object Keys {
        val INSTALLATION_ID = stringPreferencesKey("installation_id")
        val FCM_TOKEN = stringPreferencesKey("fcm_token")
        val LAST_SIGNATURE = stringPreferencesKey("last_registration_signature")
        val LAST_REGISTERED_AT = longPreferencesKey("last_registered_at")
    }

    /** Dernier enregistrement réussi : ce qui a été envoyé et quand. */
    data class LastRegistration(val signature: String, val registeredAtMillis: Long)

    suspend fun getOrCreateInstallationId(): String = withContext(ioDispatcher) {
        var installationId: String? = null
        // Lecture + création dans la même transaction DataStore : deux appels simultanés ne
        // peuvent pas créer deux identifiants différents.
        dataStore.edit { prefs ->
            installationId = prefs[Keys.INSTALLATION_ID] ?: UUID.randomUUID().toString().also {
                prefs[Keys.INSTALLATION_ID] = it
            }
        }
        checkNotNull(installationId)
    }

    suspend fun getToken(): String? = withContext(ioDispatcher) { dataStore.data.first()[Keys.FCM_TOKEN] }

    suspend fun saveToken(token: String) = withContext(ioDispatcher) {
        dataStore.edit { it[Keys.FCM_TOKEN] = token }
    }

    suspend fun getLastRegistration(): LastRegistration? = withContext(ioDispatcher) {
        val prefs = dataStore.data.first()
        val signature = prefs[Keys.LAST_SIGNATURE] ?: return@withContext null
        LastRegistration(signature, prefs[Keys.LAST_REGISTERED_AT] ?: 0L)
    }

    suspend fun saveLastRegistration(registration: LastRegistration) = withContext(ioDispatcher) {
        dataStore.edit {
            it[Keys.LAST_SIGNATURE] = registration.signature
            it[Keys.LAST_REGISTERED_AT] = registration.registeredAtMillis
        }
    }

    suspend fun clearLastRegistration() = withContext(ioDispatcher) {
        dataStore.edit {
            it.remove(Keys.LAST_SIGNATURE)
            it.remove(Keys.LAST_REGISTERED_AT)
        }
    }
}
