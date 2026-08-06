package com.arzikina.ne.domain.repository

import com.arzikina.ne.domain.model.ThemeMode
import com.arzikina.ne.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Accès aux préférences globales de l'utilisateur (thème, devise
 * principale...). Le domaine ne connaît pas DataStore : l'implémentation
 * ([com.arzikina.ne.data.repository.UserPreferencesRepositoryImpl]) est seule
 * responsable du mécanisme de persistance, comme les autres repositories
 * sont seuls responsables de Room.
 */
interface UserPreferencesRepository {
    fun observePreferences(): Flow<UserPreferences>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setCurrencyCode(currencyCode: String)
}
