package com.naniger.arzikina.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

    /**
     * `true` si les curseurs ont été produits par la règle de pagination actuelle
     * ([SyncPullCursor]). Les curseurs écrits par l'ancienne règle (reprise à `serverTime` après
     * un lot plein) ont pu dépasser des lignes jamais reçues : ils doivent être remis à zéro une
     * fois, voir [resetForCurrentPullRule].
     */
    suspend fun isPullRuleUpToDate(): Boolean = withContext(ioDispatcher) {
        (dataStore.data.firstOrNull()?.get(PULL_RULE_VERSION_KEY) ?: 0) >= CURRENT_PULL_RULE_VERSION
    }

    /**
     * Remet à zéro le curseur de chaque type de [entityTypes] et note la règle actuelle, dans une
     * seule écriture : le prochain pull retélécharge tout (sans risque, l'application d'une ligne
     * serveur est idempotente) et récupère les lignes sautées par l'ancienne règle.
     */
    suspend fun resetForCurrentPullRule(entityTypes: Collection<String>) = withContext(ioDispatcher) {
        dataStore.edit { prefs ->
            entityTypes.forEach { prefs[keyFor(it)] = 0L }
            prefs[PULL_RULE_VERSION_KEY] = CURRENT_PULL_RULE_VERSION
        }
    }

    private fun keyFor(entityType: String) = longPreferencesKey("sync_last_pulled_at_$entityType")

    private companion object {
        /** 1 (implicite) : reprise à `serverTime` ; 2 : [SyncPullCursor]. */
        const val CURRENT_PULL_RULE_VERSION = 2
        val PULL_RULE_VERSION_KEY = intPreferencesKey("sync_pull_cursor_rule_version")
    }
}
