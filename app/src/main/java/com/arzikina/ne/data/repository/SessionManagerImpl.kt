package com.arzikina.ne.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.arzikina.ne.di.SessionDataStore
import com.arzikina.ne.domain.repository.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implémentation [SessionManager] par DataStore Preferences — DataStore
 * DÉDIÉ (voir [SessionDataStore] et `di/DataStoreModule`), distinct de celui
 * des préférences d'affichage (thème/devise) : la session appartient
 * conceptuellement au module Authentification, pas aux préférences
 * générales, même si le mécanisme technique sous-jacent est identique
 * aujourd'hui.
 */
class SessionManagerImpl @Inject constructor(
    @SessionDataStore private val dataStore: DataStore<Preferences>
) : SessionManager {

    private object Keys {
        val CURRENT_USER_ID = longPreferencesKey("current_user_id")
    }

    override fun observeCurrentUserId(): Flow<Long?> =
        dataStore.data.map { preferences -> preferences[Keys.CURRENT_USER_ID] }

    override suspend fun getCurrentUserIdOnce(): Long? =
        dataStore.data.map { it[Keys.CURRENT_USER_ID] }.firstOrNull()

    override suspend fun startSession(userId: Long) {
        dataStore.edit { it[Keys.CURRENT_USER_ID] = userId }
    }

    override suspend fun clearSession() {
        dataStore.edit { it.remove(Keys.CURRENT_USER_ID) }
    }
}
