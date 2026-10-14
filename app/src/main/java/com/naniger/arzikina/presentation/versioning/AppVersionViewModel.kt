package com.naniger.arzikina.presentation.versioning

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.domain.versioning.CheckAppVersionUseCase
import com.naniger.arzikina.domain.versioning.InstalledVersionProvider
import com.naniger.arzikina.domain.versioning.UpgradeRequiredSignal
import com.naniger.arzikina.domain.versioning.VersionGateResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * État « l'app peut-elle être utilisée ? », partagé par `MainActivity` (qui ouvre l'écran de
 * blocage) et `ForcedUpdateFragment` (qui l'affiche) : un seul ViewModel de portée Activity.
 *
 * - À la création : applique d'abord le cache (instantané, hors réseau), puis interroge le serveur.
 * - [check] : appelée à chaque retour au premier plan ; espacée de [MIN_INTERVAL_MILLIS] pour ne
 *   pas solliciter le serveur à chaque aller-retour très bref. `force = true` ignore ce délai
 *   (réponse 426 du serveur, bouton « Réessayer »).
 * - Une seule vérification à la fois.
 */
@HiltViewModel
class AppVersionViewModel @Inject constructor(
    private val checkAppVersion: CheckAppVersionUseCase,
    private val installedVersionProvider: InstalledVersionProvider,
    upgradeRequiredSignal: UpgradeRequiredSignal
) : ViewModel() {

    private val _state = MutableStateFlow<VersionGateResult>(VersionGateResult.Allowed)
    val state: StateFlow<VersionGateResult> = _state.asStateFlow()

    /** Version installée, pour l'affichage « installée / requise ». */
    val installedVersionName: String get() = installedVersionProvider.installedVersion().versionName

    private var checkJob: Job? = null
    private var lastRefreshAtElapsed: Long? = null

    init {
        // Gardé dans checkJob : le check() de MainActivity.onStart, juste après, ne doublonne pas.
        checkJob = viewModelScope.launch {
            _state.value = checkAppVersion.fromCache()
            refresh()
        }
        viewModelScope.launch {
            upgradeRequiredSignal.events.collect { check(force = true) }
        }
    }

    fun check(force: Boolean = false) {
        val last = lastRefreshAtElapsed
        if (!force && last != null && SystemClock.elapsedRealtime() - last < MIN_INTERVAL_MILLIS) return
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        _state.value = checkAppVersion.refresh()
        lastRefreshAtElapsed = SystemClock.elapsedRealtime()
    }

    private companion object {
        const val MIN_INTERVAL_MILLIS = 5 * 60 * 1000L
    }
}
