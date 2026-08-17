package com.arzikina.ne.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.ThemeMode
import com.arzikina.ne.domain.repository.AuthRepository
import com.arzikina.ne.domain.repository.BiometricAuthenticator
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.UserPreferencesRepository
import com.arzikina.ne.presentation.profile.BiometricLockUiState
import com.arzikina.ne.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * État affiché par l'écran Paramètres. [fullName]/[profilePhotoUri] sont PUREMENT informatifs
 * (en-tête de profil, voir `SettingsFragment`) — contrairement à `ProfileFormState`, rien ici
 * n'est éditable, cet écran ne fait que RENVOYER vers [com.arzikina.ne.presentation.profile.ProfileFragment]
 * pour toute modification d'identité. Observé en CONTINU (pas chargé une seule fois comme
 * `ProfileViewModel.init`) : aucun formulaire en cours de saisie à protéger d'une réémission, voir
 * la doc de `ProfileFormState` pour le raisonnement inverse.
 */
data class SettingsUiState(
    val fullName: String = "",
    val profilePhotoUri: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val currencyCode: String = Constants.DEFAULT_CURRENCY_CODE
)

/**
 * ViewModel de l'écran Paramètres. Volontairement séparé de [BackupViewModel] (préférences vs
 * sauvegarde/restauration, deux responsabilités indépendantes qui ne partagent que le même écran
 * — voir la doc de tête de [BackupViewModel]).
 *
 * Grandit section par section (voir le plan "Refonte de la page Paramètres") : cette étape ne
 * couvre que l'en-tête profil et la section "Général" (devise, thème). Les étapes suivantes
 * ajouteront leurs propres méthodes ici plutôt que de créer un ViewModel par section — un seul
 * écran, un seul ViewModel, comme partout ailleurs dans le projet.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    authRepository: AuthRepository,
    sessionManager: SessionManager,
    private val biometricAuthenticator: BiometricAuthenticator
) : ViewModel() {

    /**
     * Réutilise TEL QUEL [com.arzikina.ne.presentation.profile.BiometricLockUiState] (voir sa
     * doc) : même réglage, même distinction [isAvailable]/[isEnabled] (matériel résolu une seule
     * fois vs préférence observée en continu) que [com.arzikina.ne.presentation.profile.ProfileViewModel] —
     * dupliquer une classe quasi identique n'apporterait rien.
     *
     * StateFlow SÉPARÉ de [uiState] plutôt que fusionné dedans : [uiState] est entièrement
     * RECONSTRUIT à chaque émission de `combine` (préférences + utilisateur), ce qui écraserait
     * [com.arzikina.ne.presentation.profile.BiometricLockUiState.isAvailable] à sa valeur par
     * défaut à chaque fois si ce champ y vivait — exactement le même problème que
     * [com.arzikina.ne.presentation.profile.ProfileViewModel] a déjà résolu de la même façon.
     */
    private val _biometricLockState = MutableStateFlow(BiometricLockUiState())
    val biometricLockState: StateFlow<BiometricLockUiState> = _biometricLockState.asStateFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        userPreferencesRepository.observePreferences(),
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) flowOf(null) else authRepository.observeUser(userId)
        }
    ) { preferences, user ->
        SettingsUiState(
            fullName = user?.fullName.orEmpty(),
            profilePhotoUri = user?.profilePhotoUri,
            themeMode = preferences.themeMode,
            currencyCode = preferences.currencyCode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = SettingsUiState()
    )

    init {
        viewModelScope.launch {
            val available = biometricAuthenticator.isAvailable()
            _biometricLockState.update { it.copy(isAvailable = available) }
        }
        viewModelScope.launch {
            userPreferencesRepository.observePreferences().collect { preferences ->
                _biometricLockState.update { it.copy(isEnabled = preferences.biometricLockEnabled) }
            }
        }
    }

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { userPreferencesRepository.setThemeMode(mode) }
    }

    fun onCurrencyChange(currencyCode: String) {
        viewModelScope.launch { userPreferencesRepository.setCurrencyCode(currencyCode) }
    }

    /** Voir [com.arzikina.ne.presentation.profile.ProfileViewModel.onBiometricLockToggle] : même
     * raisonnement — aucune vérification biométrique n'est demandée pour ACTIVER/DÉSACTIVER ce
     * réglage lui-même, la session locale déjà active suffit. */
    fun onBiometricLockToggle(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setBiometricLockEnabled(enabled) }
    }
}
