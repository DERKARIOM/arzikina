package com.arzikina.ne.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.SyncEngineResult
import com.arzikina.ne.domain.model.SyncPullResult
import com.arzikina.ne.domain.model.SyncQueueStatus
import com.arzikina.ne.domain.model.ThemeMode
import com.arzikina.ne.domain.repository.AuthRepository
import com.arzikina.ne.domain.repository.BiometricAuthenticator
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.SyncAuthRepository
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

/**
 * Niveaux affichés par l'indicateur visuel de `syncRow` (voir [SettingsViewModel.syncIndicatorState]).
 * [HIDDEN] : aucune session serveur active — ne rien afficher plutôt qu'un état qui ne
 * correspondrait à rien de réel (voir la KDoc de [com.arzikina.ne.domain.repository.SyncAuthRepository]
 * sur la synchronisation comme fonctionnalité additive, jamais un prérequis). Ordre de priorité des
 * trois autres niveaux — voir [SettingsViewModel.syncIndicatorState] : [ERROR] avant [SYNCING] avant
 * [PENDING], le cas le plus actionnable prenant toujours le dessus sur les autres.
 */
enum class SyncIndicatorLevel { HIDDEN, UP_TO_DATE, PENDING, SYNCING, ERROR }

/** [pendingCount] uniquement utile quand [level] vaut [SyncIndicatorLevel.PENDING] (voir
 *  `SettingsFragment.renderSyncIndicator`) — `0` par défaut ailleurs, jamais lu dans ce cas. */
data class SyncIndicatorUiState(
    val level: SyncIndicatorLevel = SyncIndicatorLevel.HIDDEN,
    val pendingCount: Int = 0
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
    private val syncEngine: SyncEngine,
    private val syncAuthRepository: SyncAuthRepository
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
     * Indicateur EN CONTINU de l'état de synchronisation, affiché dans `rowValue` de `syncRow`
     * (voir `SettingsFragment.renderSyncIndicator`) — StateFlow séparé de [uiState] pour la même
     * raison que [syncNowState]/[biometricLockState] ci-dessus. Combine la session serveur active
     * ([SyncAuthRepository.observeActiveSession]) et l'état de la file
     * ([SyncEngine.observeQueueStatus]) : sans session, [SyncIndicatorLevel.HIDDEN] quel que soit
     * le contenu de la file (elle peut légitimement contenir des entrées `PENDING` accumulées avant
     * toute connexion — rien à signaler tant que la synchronisation n'est pas activée).
     *
     * Priorité [SyncIndicatorLevel.ERROR] > [SyncIndicatorLevel.SYNCING] > [SyncIndicatorLevel.PENDING] :
     * une erreur reste le cas le plus actionnable, à ne jamais masquer par un décompte `PENDING`
     * qui inclurait ces mêmes entrées en échec (une entrée `FAILED` n'est PAS `PENDING`, voir
     * `SyncStatus`, donc les deux décomptes ne se chevauchent jamais — cet ordre est une garde
     * supplémentaire, pas une nécessité stricte ici).
     */
    val syncIndicatorState: StateFlow<SyncIndicatorUiState> = combine(
        syncAuthRepository.observeActiveSession(),
        syncEngine.observeQueueStatus()
    ) { session, queueStatus ->
        if (session == null) {
            SyncIndicatorUiState(level = SyncIndicatorLevel.HIDDEN)
        } else {
            val level = when {
                queueStatus.failed > 0 -> SyncIndicatorLevel.ERROR
                queueStatus.syncing > 0 -> SyncIndicatorLevel.SYNCING
                queueStatus.pending > 0 -> SyncIndicatorLevel.PENDING
                else -> SyncIndicatorLevel.UP_TO_DATE
            }
            SyncIndicatorUiState(level = level, pendingCount = queueStatus.pending)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = SyncIndicatorUiState()
    )

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
