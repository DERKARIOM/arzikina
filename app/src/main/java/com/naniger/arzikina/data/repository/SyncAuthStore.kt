package com.naniger.arzikina.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.naniger.arzikina.data.security.TokenCipher
import com.naniger.arzikina.di.IoDispatcher
import com.naniger.arzikina.di.SyncAuthDataStore
import com.naniger.arzikina.domain.model.SyncSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accès bas niveau, partagé, au [DataStore] de session de synchronisation — seul point de
 * lecture/écriture du token chiffré (voir [TokenCipher]). Interne à la couche data : ni
 * [com.naniger.arzikina.domain.repository.SyncAuthRepository] (implémenté par [SyncAuthRepositoryImpl],
 * qui délègue ici) ni un futur `SyncTokenProvider` interne (destiné à un intercepteur réseau)
 * n'accèdent au [DataStore] directement — évite de dupliquer la logique de (dé)chiffrement/lecture
 * des clés entre les deux implémentations (voir instructions projet : "Évite absolument le code
 * dupliqué").
 */
@Singleton
class SyncAuthStore @Inject constructor(
    @SyncAuthDataStore private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private object Keys {
        val SERVER_USER_ID = stringPreferencesKey("server_user_id")
        val TOKEN_CIPHERTEXT = stringPreferencesKey("token_ciphertext")
        val TOKEN_IV = stringPreferencesKey("token_iv")
        val EXPIRES_AT = longPreferencesKey("expires_at")
        /** Ajoutée pour le login unifié (voir `SyncSession.fullName`) — absente d'une session
         *  sauvegardée par une version antérieure de l'app, voir le repli dans [toSessionOrNull]. */
        val FULL_NAME = stringPreferencesKey("full_name")
    }

    suspend fun saveSession(userId: String, rawToken: String, expiresAt: Long, fullName: String) =
        withContext(ioDispatcher) {
            val encrypted = TokenCipher.encrypt(rawToken)
            dataStore.edit { prefs ->
                prefs[Keys.SERVER_USER_ID] = userId
                prefs[Keys.TOKEN_CIPHERTEXT] = encrypted.ciphertextBase64
                prefs[Keys.TOKEN_IV] = encrypted.ivBase64
                prefs[Keys.EXPIRES_AT] = expiresAt
                prefs[Keys.FULL_NAME] = fullName
            }
        }

    suspend fun clear() = withContext(ioDispatcher) { dataStore.edit { it.clear() } }

    suspend fun getActiveSession(): SyncSession? =
        withContext(ioDispatcher) { dataStore.data.firstOrNull()?.let(::toSessionOrNull) }

    fun observeActiveSession(): Flow<SyncSession?> = dataStore.data.map(::toSessionOrNull)

    suspend fun getValidRawToken(): String? = withContext(ioDispatcher) {
        val prefs = dataStore.data.firstOrNull() ?: return@withContext null
        if (toSessionOrNull(prefs) == null) return@withContext null // absent ou expiré
        val ciphertext = prefs[Keys.TOKEN_CIPHERTEXT] ?: return@withContext null
        val iv = prefs[Keys.TOKEN_IV] ?: return@withContext null
        try {
            TokenCipher.decrypt(ciphertext, iv)
        } catch (e: GeneralSecurityException) {
            // Le ciphertext stocké ne correspond plus à la clé Android Keystore actuelle (voir
            // AesGcmKeystoreCipher.getOrCreateKey() : réinstallation, effacement partiel des
            // données, restauration de sauvegarde sans le Keystore...) — cas documenté comme
            // "attendu" mais qui ne doit JAMAIS faire planter l'app : sans ce catch, l'exception
            // remonte non interceptée (souvent depuis un thread de fond, ex. SyncWorker au
            // démarrage) et tue tout le process, en boucle, sans jamais laisser l'utilisateur
            // atteindre un écran de connexion. On traite ce cas comme une session absente — la
            // session locale corrompue est effacée pour ne pas re-tenter ce déchiffrement voué à
            // l'échec à chaque appel, l'utilisateur devra simplement se reconnecter.
            clear()
            null
        }
    }

    private fun toSessionOrNull(prefs: Preferences): SyncSession? {
        val userId = prefs[Keys.SERVER_USER_ID] ?: return null
        val expiresAt = prefs[Keys.EXPIRES_AT] ?: return null
        if (expiresAt < System.currentTimeMillis()) return null
        // Repli "" : une session sauvegardée par une version de l'app antérieure au login unifié
        // n'a jamais eu de fullName persisté — ne doit jamais faire échouer la lecture de la
        // session existante pour autant (voir Keys.FULL_NAME).
        val fullName = prefs[Keys.FULL_NAME] ?: ""
        return SyncSession(serverUserId = userId, expiresAt = expiresAt, fullName = fullName)
    }
}
