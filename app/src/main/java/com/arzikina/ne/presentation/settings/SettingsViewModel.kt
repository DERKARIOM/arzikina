package com.arzikina.ne.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.ThemeMode
import com.arzikina.ne.domain.repository.AuthRepository
import com.arzikina.ne.domain.repository.BiometricAuthenticator
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.SyncAuthRepository
import com.arzikina.ne.domain.repository.UserPreferencesRepository
import com.arzikina.ne.presentation.components.SyncButtonController
import com.arzikina.ne.presentation.components.SyncButtonEvent
import com.arzikina.ne.presentation.components.SyncIndicatorUiState
import com.arzikina.ne.presentation.components.SyncNowUiState
import com.arzikina.ne.presentation.profile.BiometricLockUiState
import com.arzikina.ne.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    val themeMode: ThemeMode = ThemeMode.DARK,
    val currencyCode: String = Constants.DEFAULT_CURRENCY_CODE
)

/**
 * État de la carte "Serveur de synchronisation" (voir `SettingsFragment.setUpSyncSection`) —
 * remplace l'ancien raccourci vers `SyncLoginFragment` (étape D4 du chantier "audit auth + sync +
 * doublons") : la connexion au serveur se fait désormais UNIQUEMENT via l'écran de connexion
 * unique de l'app (`presentation/auth/LoginFragment`, voir `UnifiedAuthRepository`), jamais depuis
 * Paramètres — cette carte ne fait plus qu'AFFICHER l'état courant et permettre la déconnexion.
 * [isConnected] `false` : aucune session serveur active, ex. utilisateur connecté en mode
 * hors-ligne (voir `UnifiedAuthResult.Success.usedLocalFallback`) — rien à faire ici tant qu'une
 * connexion en ligne n'a pas eu lieu au moins une fois.
 */
data class SyncAccountUiState(
    val isConnected: Boolean = false,
    val fullName: String = ""
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
    private val biometricAuthenticator: BiometricAuthenticator,
    private val syncButtonController: SyncButtonController,
    private val syncAuthRepository: SyncAuthRepository
) : ViewModel() {

    /** Voir [SyncButtonController] : logique partagée avec `DashboardViewModel`, extraite de cet
     *  écran (seul appelant jusqu'à l'ajout du bouton de sync sur le Dashboard) pour éviter de
     *  dupliquer le combine session+file d'attente et la garde de ré-entrance `syncNow`. */
    val events: SharedFlow<SyncButtonEvent> = syncButtonController.events

    /** Séparé de [uiState] pour la même raison que [biometricLockState] ci-dessous : [uiState] est
     *  entièrement reconstruit à chaque émission de `combine`, ce qui écraserait [isSyncing] à
     *  `false` en plein milieu d'une synchronisation dès que les préférences/l'utilisateur émettent
     *  pour une tout autre raison. */
    val syncNowState: StateFlow<SyncNowUiState> = syncButtonController.syncNowState

    /**
     * Indicateur EN CONTINU de l'état de synchronisation, affiché dans `rowValue` de `syncRow`
     * (voir `SettingsFragment.renderSyncIndicator`) — voir [SyncButtonController.indicatorState]
     * pour le détail du combine et de la priorité des niveaux.
     */
    val syncIndicatorState: StateFlow<SyncIndicatorUiState> = syncButtonController.indicatorState(viewModelScope)

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

    /** Voir [SyncAccountUiState] — dérivé EN CONTINU de [SyncAuthRepository.observeActiveSession],
     *  même source que [SyncButtonController.indicatorState] (aucune donnée dupliquée). */
    val syncAccountState: StateFlow<SyncAccountUiState> = syncAuthRepository.observeActiveSession()
        .map { session -> SyncAccountUiState(isConnected = session != null, fullName = session?.fullName.orEmpty()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = SyncAccountUiState()
        )

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

    /** Voir [SyncButtonController.syncNow] pour le détail (ordre push/pull, garde de ré-entrance). */
    fun syncNow() = syncButtonController.syncNow(viewModelScope)

    /**
     * Supprime UNIQUEMENT la session serveur (voir [SyncAuthRepository.logout]) — ni la session
     * locale ([SessionManager], inchangée : l'utilisateur reste connecté à l'app), ni les données
     * déjà synchronisées. [syncAccountState] repasse à `isConnected = false` dès la confirmation
     * (voir [SettingsFragment], dialogue de confirmation avant l'appel).
     */
    fun disconnectSyncAccount() {
        viewModelScope.launch { syncAuthRepository.logout() }
    }
}
