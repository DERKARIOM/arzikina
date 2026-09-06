package com.arzikina.ne.presentation.components

import com.arzikina.ne.domain.model.SyncEngineResult
import com.arzikina.ne.domain.model.SyncPullResult
import com.arzikina.ne.domain.repository.ProfilePhotoRepository
import com.arzikina.ne.domain.repository.SyncAuthRepository
import com.arzikina.ne.domain.repository.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Voir [SyncButtonController.syncNowState] pour le raisonnement sur cet état séparé. */
data class SyncNowUiState(
    val isSyncing: Boolean = false
)

/**
 * Niveaux affichés par un indicateur visuel de synchronisation (voir
 * [SyncButtonController.indicatorState]). [HIDDEN] : aucune session serveur active — ne rien
 * afficher plutôt qu'un état qui ne correspondrait à rien de réel (voir la KDoc de
 * [SyncAuthRepository] sur la synchronisation comme fonctionnalité additive, jamais un
 * prérequis). Ordre de priorité des trois autres niveaux — voir [SyncButtonController.indicatorState] :
 * [ERROR] avant [SYNCING] avant [PENDING], le cas le plus actionnable prenant toujours le dessus
 * sur les autres.
 */
enum class SyncIndicatorLevel { HIDDEN, UP_TO_DATE, PENDING, SYNCING, ERROR }

/** [pendingCount] uniquement utile quand [level] vaut [SyncIndicatorLevel.PENDING] — `0` par
 *  défaut ailleurs, jamais lu dans ce cas. */
data class SyncIndicatorUiState(
    val level: SyncIndicatorLevel = SyncIndicatorLevel.HIDDEN,
    val pendingCount: Int = 0
)

/** Événement ponctuel (Snackbar) suite à [SyncButtonController.syncNow]. [SyncFinished] porte les
 *  DEUX résultats (push et pull, voir [SyncButtonController.syncNow] qui enchaîne toujours les
 *  deux) : un seul événement plutôt que deux émissions distinctes, pour que l'écran appelant
 *  affiche un seul message résumant l'aller-retour complet. */
sealed interface SyncButtonEvent {
    data class SyncFinished(val pushResult: SyncEngineResult, val pullResult: SyncPullResult) : SyncButtonEvent
    data class SyncError(val message: String) : SyncButtonEvent
}

/**
 * Logique du bouton "Synchroniser maintenant", partagée entre tous les écrans qui l'exposent
 * (Paramètres, Dashboard — voir `SettingsViewModel`/`DashboardViewModel`) : combine la session
 * serveur active et l'état de la file d'attente pour produire un indicateur visuel, et pilote un
 * aller-retour push+pull avec garde de ré-entrance. Extrait de `SettingsViewModel` (seul appelant
 * jusqu'ici) au moment où le Dashboard a eu besoin exactement du même comportement — dupliquer ces
 * ~30 lignes (combine à 2 flux, priorité des niveaux, garde `isSyncing`) aurait violé la règle
 * projet "pas de code dupliqué" pour un futur correctif (voir le bug 22.5b) qu'il aurait fallu
 * appliquer deux fois.
 *
 * PAS de scope Hilt (`@Singleton`/`@ViewModelScoped`) : chaque ViewModel injecteur reçoit sa PROPRE
 * instance, ce qui est correct ici — [_syncNowState] n'est qu'une garde locale "un appel réseau est
 * en cours DEPUIS CET ÉCRAN", pas un état partagé entre écrans (l'indicateur visuel, lui, reste
 * cohérent entre écrans car dérivé de la vérité partagée `SyncEngine.observeQueueStatus()`, pas de
 * [_syncNowState]).
 */
class SyncButtonController @Inject constructor(
    private val syncEngine: SyncEngine,
    private val syncAuthRepository: SyncAuthRepository,
    private val profilePhotoRepository: ProfilePhotoRepository
) {
    private val _syncNowState = MutableStateFlow(SyncNowUiState())
    val syncNowState: StateFlow<SyncNowUiState> = _syncNowState.asStateFlow()

    private val _events = MutableSharedFlow<SyncButtonEvent>()
    val events: SharedFlow<SyncButtonEvent> = _events.asSharedFlow()

    /**
     * [scope] est fourni par l'appelant (`viewModelScope` de l'écran) plutôt que capturé à la
     * construction : cette classe n'ayant pas de scope Hilt propre, elle ne possède aucun
     * `CoroutineScope` à elle pour appeler `stateIn` — voir la KDoc de classe.
     */
    fun indicatorState(scope: CoroutineScope): StateFlow<SyncIndicatorUiState> = combine(
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
        scope = scope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = SyncIndicatorUiState()
    )

    /**
     * Enchaîne TOUJOURS push PUIS pull (voir la KDoc de [SyncEngine.pullRemoteChanges] sur cet
     * ordre), PUIS la photo de profil ([ProfilePhotoRepository.syncWithServer]) — même ordre et
     * même raisonnement que [com.arzikina.ne.work.SyncWorker.doWork] : la photo doit suivre
     * EXACTEMENT le même chemin que le déclenchement automatique, sinon ce bouton "Synchroniser
     * maintenant" redevient un moyen de forcer la synchronisation des autres données SANS jamais
     * pouvoir forcer celle de la photo (bug corrigé ici — la photo restait bloquée en attente
     * jusqu'au prochain cycle automatique, sans que l'utilisateur ait de recours manuel).
     *
     * Ignore un appel pendant qu'une synchronisation est déjà en cours DEPUIS CE CONTRÔLEUR — même
     * garde que `BackupViewModel` sur export/import.
     */
    fun syncNow(scope: CoroutineScope) {
        if (_syncNowState.value.isSyncing) return
        scope.launch {
            _syncNowState.update { it.copy(isSyncing = true) }
            runCatching {
                val pushResult = syncEngine.pushPendingChanges()
                val pullResult = syncEngine.pullRemoteChanges()
                profilePhotoRepository.syncWithServer()
                pushResult to pullResult
            }
                .onSuccess { (pushResult, pullResult) -> _events.emit(SyncButtonEvent.SyncFinished(pushResult, pullResult)) }
                .onFailure { _events.emit(SyncButtonEvent.SyncError(it.message ?: "Erreur inconnue")) }
            _syncNowState.update { it.copy(isSyncing = false) }
        }
    }
}
