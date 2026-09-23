package com.naniger.arzikina.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.naniger.arzikina.di.IoDispatcher
import com.naniger.arzikina.di.SyncAuthDataStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mémorise, PAR type d'entité, l'horodatage `updated_after` du dernier pull réussi (voir
 * `server/api/sync/pull.php`, `SyncEngineImpl.pullRemoteChanges`) — un pull incrémental, jamais un
 * dump complet de la table à chaque appel (voir la doc de tête de `pull.php`).
 *
 * Réutilise le [DataStore] qualifié `@SyncAuthDataStore` (même fichier que [SyncAuthStore]) plutôt
 * qu'un nouveau : ce curseur appartient au même regroupement logique — "relation de CE profil avec
 * le serveur de synchronisation" — que le token/la session qui y vivent déjà (voir la KDoc de
 * `SyncAuthDataStore` dans `di/DataStoreModule.kt`). Classe SÉPARÉE de [SyncAuthStore] malgré tout
 * (Single Responsibility) : ce dernier reste focalisé sur le token, celui-ci sur la progression du
 * pull — deux préoccupations qui évoluent indépendamment (ex. un pull peut avancer sans jamais
 * toucher au token).
 *
 * Clé dynamique (`sync_last_pulled_at_<entityType>`) plutôt qu'une constante unique : anticipe
 * l'ajout d'une deuxième entité (chacune a son propre curseur, indépendant) sans repasser par une
 * migration de préférences.
 */
@Singleton
class SyncCursorStore @Inject constructor(
    @SyncAuthDataStore private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun getLastPulledAt(entityType: String): Long = withContext(ioDispatcher) {
        dataStore.data.firstOrNull()?.get(keyFor(entityType)) ?: 0L
    }

    suspend fun setLastPulledAt(entityType: String, serverTime: Long) = withContext(ioDispatcher) {
        dataStore.edit { prefs -> prefs[keyFor(entityType)] = serverTime }
    }

    private fun keyFor(entityType: String) = longPreferencesKey("sync_last_pulled_at_$entityType")
}
