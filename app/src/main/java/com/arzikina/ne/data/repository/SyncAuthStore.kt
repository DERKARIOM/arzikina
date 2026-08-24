package com.arzikina.ne.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arzikina.ne.data.security.TokenCipher
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.di.SyncAuthDataStore
import com.arzikina.ne.domain.model.SyncSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accès bas niveau, partagé, au [DataStore] de session de synchronisation — seul point de
 * lecture/écriture du token chiffré (voir [TokenCipher]). Interne à la couche data : ni
 * [com.arzikina.ne.domain.repository.SyncAuthRepository] (implémenté par [SyncAuthRepositoryImpl],
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
    }

    suspend fun saveSession(userId: String, rawToken: String, expiresAt: Long) =
        withContext(ioDispatcher) {
            val encrypted = TokenCipher.encrypt(rawToken)
            dataStore.edit { prefs ->
                prefs[Keys.SERVER_USER_ID] = userId
                prefs[Keys.TOKEN_CIPHERTEXT] = encrypted.ciphertextBase64
                prefs[Keys.TOKEN_IV] = encrypted.ivBase64
                prefs[Keys.EXPIRES_AT] = expiresAt
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
        TokenCipher.decrypt(ciphertext, iv)
    }

    private fun toSessionOrNull(prefs: Preferences): SyncSession? {
        val userId = prefs[Keys.SERVER_USER_ID] ?: return null
        val expiresAt = prefs[Keys.EXPIRES_AT] ?: return null
        if (expiresAt < System.currentTimeMillis()) return null
        return SyncSession(serverUserId = userId, expiresAt = expiresAt)
    }
}
