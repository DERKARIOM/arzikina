package com.arzikina.ne.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.SyncEngineResult
import com.arzikina.ne.domain.model.SyncPullResult
import com.arzikina.ne.domain.model.ThemeMode
import com.arzikina.ne.domain.repository.AuthRepository
import com.arzikina.ne.domain.repository.BiometricAuthenticator
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.SyncEngine
import com.arzikina.ne.domain.repository.UserPreferencesRepository
import com.arzikina.ne.presentation.profile.BiometricLockUiState
import com.arzikina.ne.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

/** Voir [SettingsViewModel.syncNowState] pour le raisonnement sur cet état séparé. */
data class SyncNowUiState(
    val isSyncing: Boolean = false
)

/** Événement ponctuel (Snackbar) suite à [SettingsViewModel.syncNow] — même principe que
 *  `BackupEvent` ([BackupViewModel]). [SyncFinished] porte les DEUX résultats (push et pull, voir
 *  [SettingsViewModel.syncNow] qui enchaîne toujours les deux) : un seul événement plutôt que deux
 *  émissions distinctes, pour que [SettingsFragment] affiche un seul Snackbar résumant l'aller-retour
 *  complet, jamais deux Snackbars successifs pour une seule action utilisateur. */
sealed interface SettingsEvent {
    data class SyncFinished(val pushResult: SyncEngineResult, val pullResult: SyncPullResult) : SettingsEvent
    data class SyncError(val message: String) : SettingsEvent
}

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
    private val syncEngine: SyncEngine
) : ViewModel() {

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    /** Séparé de [uiState] pour la même raison que [biometricLockState] ci-dessous : [uiState] est
     *  entièrement reconstruit à chaque émission de `combine`, ce qui écraserait [isSyncing] à
     *  `false` en plein milieu d'une synchronisation dès que les préférences/l'utilisateur émettent
     *  pour une tout autre raison. */
    private val _syncNowState = MutableStateFlow(SyncNowUiState())
    val syncNowState: StateFlow<SyncNowUiState> = _syncNowState.asStateFlow()

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

    /**
     * Voir [com.arzikina.ne.domain.repository.SyncEngine] pour l'étape actuelle (aucun
     * déclenchement automatique, cette méthode est le premier appelant réel). Enchaîne TOUJOURS
     * push PUIS pull (voir la KDoc de [com.arzikina.ne.domain.repository.SyncEngine.pullRemoteChanges]
     * sur cet ordre) — un aller-retour complet à chaque tap, jamais l'un sans l'autre. Si le push
     * lève une exception, le pull n'est PAS tenté (voir `runCatching` : la première exception
     * interrompt le bloc) — inutile de recevoir avant d'avoir confirmé l'envoi, et l'utilisateur
     * peut simplement retaper.
     *
     * Ignore un appel pendant qu'une synchronisation est déjà en cours (même garde que
     * [BackupViewModel] sur export/import) — évite un double envoi du même lot si l'utilisateur
     * tape deux fois avant que la ligne n'affiche son indicateur.
     */
    fun syncNow() {
        if (_syncNowState.value.isSyncing) return
        viewModelScope.launch {
            _syncNowState.update { it.copy(isSyncing = true) }
            runCatching {
                val pushResult = syncEngine.pushPendingChanges()
                val pullResult = syncEngine.pullRemoteChanges()
                pushResult to pullResult
            }
                .onSuccess { (pushResult, pullResult) -> _events.emit(SettingsEvent.SyncFinished(pushResult, pullResult)) }
                .onFailure { _events.emit(SettingsEvent.SyncError(it.message ?: "Erreur inconnue")) }
            _syncNowState.update { it.copy(isSyncing = false) }
        }
    }
}
