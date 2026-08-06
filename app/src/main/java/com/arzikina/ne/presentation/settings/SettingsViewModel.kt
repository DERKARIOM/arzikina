package com.arzikina.ne.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.ThemeMode
import com.arzikina.ne.domain.model.UserPreferences
import com.arzikina.ne.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel de l'écran Paramètres. Contrairement aux autres écrans de
 * l'application, l'état n'a pas besoin d'être enveloppé dans [com.arzikina.ne.util.AppResult] :
 * DataStore renvoie toujours des valeurs par défaut valides (voir
 * [UserPreferencesRepository]), il n'y a donc pas d'état "Chargement"/"Erreur"
 * à distinguer d'un état "Succès" vide.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = userPreferencesRepository.observePreferences()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { userPreferencesRepository.setThemeMode(mode) }
    }

    fun onCurrencyChange(currencyCode: String) {
        viewModelScope.launch { userPreferencesRepository.setCurrencyCode(currencyCode) }
    }
}
