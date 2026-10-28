package com.naniger.arzikina.presentation.auth.usage

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.UnifiedAuthError
import com.naniger.arzikina.domain.model.UsageMode
import com.naniger.arzikina.domain.model.UsageModeSelectionResult
import com.naniger.arzikina.domain.repository.SessionManager
import com.naniger.arzikina.domain.repository.UnifiedAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UsageModeUiState(
    val selected: UsageMode? = null,
    val isSubmitting: Boolean = false
)

sealed interface UsageModeEvent {
    /** Choix enregistré et comptes créés : la session est démarrée, direction l'accueil. */
    data object Done : UsageModeEvent
    data class ShowError(@StringRes val messageRes: Int) : UsageModeEvent
}

/**
 * Choix du mode d'utilisation d'un compte Google (nouveau, ou jamais configuré). La session de l'application n'est
 * démarrée QU'APRÈS la réussite : si l'application est fermée avant, la prochaine connexion Google reproposera ce
 * choix (le serveur n'a ni mode ni comptes pour cet utilisateur) — aucun utilisateur n'entre dans l'appli sans
 * configuration.
 */
@HiltViewModel
class UsageModeViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val unifiedAuthRepository: UnifiedAuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val localUserId: Long = savedState.get<Long>(ARG_LOCAL_USER_ID) ?: 0L

    private val _state = MutableStateFlow(UsageModeUiState())
    val state: StateFlow<UsageModeUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<UsageModeEvent>()
    val events: SharedFlow<UsageModeEvent> = _events.asSharedFlow()

    fun onModeSelected(mode: UsageMode) {
        if (_state.value.isSubmitting) return
        _state.update { it.copy(selected = mode) }
    }

    /** Anti double appui : tant qu'un envoi est en cours, tout nouvel appel est ignoré. */
    fun confirm() {
        val current = _state.value
        val mode = current.selected ?: return
        if (current.isSubmitting || localUserId == 0L) return
        _state.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            when (val result = unifiedAuthRepository.completeUsageModeSelection(localUserId, mode)) {
                UsageModeSelectionResult.Success -> {
                    sessionManager.startSession(localUserId)
                    _state.update { it.copy(isSubmitting = false) }
                    _events.emit(UsageModeEvent.Done)
                }
                is UsageModeSelectionResult.Failure -> {
                    _state.update { it.copy(isSubmitting = false) }
                    _events.emit(UsageModeEvent.ShowError(result.error.messageRes()))
                }
            }
        }
    }

    private fun UnifiedAuthError.messageRes(): Int = when (this) {
        UnifiedAuthError.NetworkUnavailableNoLocalFallback -> R.string.sync_login_error_network
        is UnifiedAuthError.ServerError -> R.string.sync_login_error_server
        else -> R.string.register_error_unknown
    }

    companion object {
        const val ARG_LOCAL_USER_ID = "localUserId"
    }
}
