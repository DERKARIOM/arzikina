package com.arzikina.ne.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arzikina.ne.domain.model.ThemeMode
import com.arzikina.ne.domain.model.UserPreferences
import com.arzikina.ne.domain.repository.UserPreferencesRepository
import com.arzikina.ne.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implémentation [UserPreferencesRepository] basée sur DataStore Preferences.
 *
 * Choix technique : DataStore Preferences (clé-valeur) plutôt que DataStore
 * Proto ou Room, car il n'y a que deux réglages scalaires aujourd'hui et pas
 * de besoin de schéma fort ni de requêtes — cohérent avec ce que DataStore
 * est conçu pour remplacer (SharedPreferences), sans le coût d'une table Room
 * pour une seule ligne. Si les préférences se complexifient nettement plus
 * tard, une migration vers DataStore Proto (schéma typé) reste possible sans
 * changer l'interface du domaine.
 */
class UserPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : UserPreferencesRepository {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CURRENCY_CODE = stringPreferencesKey("currency_code")
    }

    override fun observePreferences(): Flow<UserPreferences> =
        dataStore.data.map { preferences ->
            val themeMode = preferences[Keys.THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM
            val currencyCode = preferences[Keys.CURRENCY_CODE] ?: Constants.DEFAULT_CURRENCY_CODE
            UserPreferences(themeMode = themeMode, currencyCode = currencyCode)
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setCurrencyCode(currencyCode: String) {
        dataStore.edit { it[Keys.CURRENCY_CODE] = currencyCode }
    }
}
