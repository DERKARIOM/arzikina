package com.arzikina.ne.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arzikina.ne.data.local.dao.UserPreferencesDao
import com.arzikina.ne.data.local.entity.UserPreferencesEntity
import com.arzikina.ne.data.remote.dto.UserPreferencesSyncPayload
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.SyncOperation
import com.arzikina.ne.domain.model.ThemeMode
import com.arzikina.ne.domain.model.UserPreferences
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.UserPreferencesRepository
import com.arzikina.ne.util.Constants
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

/**
 * Implémentation [UserPreferencesRepository] — HYBRIDE depuis l'étape 22 (chantier de
 * synchronisation) : [ThemeMode]/[currencyCode][UserPreferences.currencyCode] vivent désormais dans
 * Room ([UserPreferencesDao]/[UserPreferencesEntity], synchronisables), [biometricLockEnabled]
 * reste EXCLUSIVEMENT dans DataStore Preferences, PAR APPAREIL (voir sa KDoc de tête dans
 * `UserPreferences.kt` — ce réglage n'est jamais scopé par utilisateur, choix assumé, jamais
 * synchronisé).
 *
 * DataStore continue NÉANMOINS de recevoir une copie de `themeMode`/`currencyCode` à chaque
 * écriture ([setThemeMode]/[setCurrencyCode]) — voir [upsertPreferences] — pour DEUX raisons :
 * 1. `MainActivity.applyStoredThemeMode()` lit la préférence de thème AVANT toute vérification de
 *    session (l'écran de connexion doit lui-même respecter le dernier thème choisi sur cet
 *    appareil) — Room seul ne peut pas répondre à cette lecture, `userId` n'existe pas encore.
 * 2. Migration en douceur des installations déjà existantes (valeurs DataStore d'avant l'étape 22) :
 *    la toute première fois qu'un utilisateur touche à un réglage après la mise à jour, la valeur
 *    DataStore déjà présente pour L'AUTRE champ (non modifié par cet appel) est reprise telle
 *    quelle dans la nouvelle ligne Room plutôt que silencieusement réinitialisée — voir
 *    [upsertPreferences]. Tant qu'aucun réglage n'a encore été modifié depuis la mise à jour (ligne
 *    Room absente), [observePreferences] retombe sur ces mêmes valeurs DataStore existantes : AUCUNE
 *    régression visible pour un utilisateur qui n'ouvre jamais Paramètres.
 *
 * Conséquence assumée (déjà vraie AVANT cette étape pour `themeMode`/`currencyCode`, puisque
 * DataStore n'est scopé par aucun utilisateur) : sur un appareil partagé par plusieurs comptes,
 * cette copie DataStore peut transitoirement refléter le dernier compte à avoir écrit, jusqu'à ce
 * que CHAQUE compte ait sa propre ligne Room (créée à la première écriture OU reçue par
 * synchronisation depuis un autre appareil du même compte) — fenêtre strictement plus courte que le
 * comportement précédent (permanent), pas une régression.
 */
class UserPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val userPreferencesDao: UserPreferencesDao,
    private val sessionManager: SessionManager,
    private val syncQueueEnqueuer: SyncQueueEnqueuer,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : UserPreferencesRepository {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CURRENCY_CODE = stringPreferencesKey("currency_code")

        /** Voir [UserPreferences.biometricLockEnabled] : réglage par appareil (cette clé DataStore
         * n'est pas scopée par utilisateur), volontairement absente de tout couple userId/clé. */
        val BIOMETRIC_LOCK_ENABLED = booleanPreferencesKey("biometric_lock_enabled")
    }

    /**
     * `userId == null` (aucune session, voir `MainActivity.applyStoredThemeMode`/
     * `resolveStartDestination`) : DataStore reste la SEULE source possible, comme avant l'étape 22
     * — voir la KDoc de tête. `userId != null` ET ligne Room présente : Room fait autorité pour
     * `themeMode`/`currencyCode` (ignore alors la copie DataStore, potentiellement périmée). Ligne
     * Room absente (utilisateur existant pas encore migré, voir [upsertPreferences]) : retombe sur
     * DataStore, exactement le même résultat qu'avant cette étape.
     */
    override fun observePreferences(): Flow<UserPreferences> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                dataStore.data.map { it.toLegacyUserPreferences() }
            } else {
                combine(userPreferencesDao.observeForUser(userId), dataStore.data) { room, preferences ->
                    val biometricLockEnabled = preferences[Keys.BIOMETRIC_LOCK_ENABLED] ?: false
                    if (room != null) {
                        UserPreferences(
                            themeMode = room.themeMode,
                            currencyCode = room.currencyCode,
                            biometricLockEnabled = biometricLockEnabled
                        )
                    } else {
                        preferences.toLegacyUserPreferences()
                    }
                }
            }
        }

    override suspend fun setThemeMode(mode: ThemeMode) = withContext(ioDispatcher) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
        upsertPreferences(themeMode = mode)
    }

    override suspend fun setCurrencyCode(currencyCode: String) = withContext(ioDispatcher) {
        dataStore.edit { it[Keys.CURRENCY_CODE] = currencyCode }
        upsertPreferences(currencyCode = currencyCode)
    }

    override suspend fun setBiometricLockEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BIOMETRIC_LOCK_ENABLED] = enabled }
    }

    /**
     * Écrit la ligne Room courante de l'utilisateur connecté, en ne changeant QUE le champ fourni
     * ([themeMode] ou [currencyCode], jamais les deux à la fois puisque [setThemeMode]/
     * [setCurrencyCode] n'en changent qu'un chacun) — l'AUTRE champ est repris de la ligne Room
     * existante si elle existe déjà, sinon de la copie DataStore encore présente (voir la KDoc de
     * tête, migration en douceur), sinon de sa valeur par défaut.
     *
     * Ne fait rien silencieusement sans utilisateur courant (`?: return`, même garde que
     * `CategoryRepositoryImpl.saveCategory`) : ne devrait pas arriver en pratique, l'écran
     * Paramètres exige déjà une session active.
     */
    private suspend fun upsertPreferences(themeMode: ThemeMode? = null, currencyCode: String? = null) {
        val userId = sessionManager.getCurrentUserIdOnce() ?: return
        val existing = userPreferencesDao.getForUser(userId)
        // Lu seulement si [existing] est `null` (une seule fois par utilisateur au maximum, voir la
        // KDoc de tête) : pas besoin de payer le coût d'une lecture DataStore à chaque écriture.
        val legacy = if (existing == null) dataStore.data.first().toLegacyUserPreferences() else null
        val now = System.currentTimeMillis()

        val entity = UserPreferencesEntity(
            id = existing?.id ?: 0L,
            userId = userId,
            themeMode = themeMode ?: existing?.themeMode ?: legacy?.themeMode ?: ThemeMode.SYSTEM,
            currencyCode = currencyCode ?: existing?.currencyCode ?: legacy?.currencyCode ?: Constants.DEFAULT_CURRENCY_CODE,
            createdAt = existing?.createdAt ?: now,
            syncId = existing?.syncId ?: UUID.randomUUID().toString(),
            updatedAt = now,
            deletedAt = existing?.deletedAt,
            version = existing?.version ?: 1
        )
        userPreferencesDao.upsert(entity)
        enqueuePreferencesSync(entity, operation = if (existing == null) SyncOperation.CREATE else SyncOperation.UPDATE)
    }

    private fun Preferences.toLegacyUserPreferences(): UserPreferences {
        val themeMode = this[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        val currencyCode = this[Keys.CURRENCY_CODE] ?: Constants.DEFAULT_CURRENCY_CODE
        val biometricLockEnabled = this[Keys.BIOMETRIC_LOCK_ENABLED] ?: false
        return UserPreferences(themeMode, currencyCode, biometricLockEnabled)
    }

    /**
     * AUCUNE référence croisée à résoudre (voir la KDoc de tête de `UserPreferencesSyncPayload.kt`)
     * — le payload le plus simple de tout le projet, construit directement depuis [entity].
     */
    private suspend fun enqueuePreferencesSync(entity: UserPreferencesEntity, operation: SyncOperation) {
        val payload = UserPreferencesSyncPayload(
            id = requireNotNull(entity.syncId) { "syncId doit être généré avant l'enfilage." },
            baseVersion = if (operation == SyncOperation.CREATE) null else entity.version,
            themeMode = entity.themeMode.name,
            currencyCode = entity.currencyCode,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
        syncQueueEnqueuer.enqueue(
            entityType = "user_preferences",
            entitySyncId = payload.id,
            operation = operation,
            payloadJson = json.encodeToString(UserPreferencesSyncPayload.serializer(), payload)
        )
    }
}
