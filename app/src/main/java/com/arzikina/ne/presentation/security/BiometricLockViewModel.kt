package com.arzikina.ne.presentation.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.repository.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Événement ponctuel consommé par [BiometricLockFragment] pour déclencher la navigation qui suit. */
sealed interface BiometricLockEvent {
    data object Unlocked : BiometricLockEvent
    data object LoggedOut : BiometricLockEvent
}

/**
 * Délibérément DÉPOURVU de toute logique `BiometricPrompt` (voir [BiometricLockFragment], qui
 * l'invoque directement avec `requireActivity()`) : un `ViewModel` ne doit jamais retenir de
 * référence à une `FragmentActivity` — elle survivrait à sa destruction lors d'une rotation/un
 * changement de configuration, une fuite mémoire classique. Ce ViewModel ne porte donc que ce qui
 * n'a pas besoin de l'Activity : la déconnexion (voir [onLogout]).
 */
@HiltViewModel
class BiometricLockViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _events = MutableSharedFlow<BiometricLockEvent>()
    val events: SharedFlow<BiometricLockEvent> = _events.asSharedFlow()

    /** Appelé par [BiometricLockFragment] après un succès `BiometricPrompt`. */
    fun onUnlocked() {
        viewModelScope.launch { _events.emit(BiometricLockEvent.Unlocked) }
    }

    /**
     * Échappatoire volontaire (voir `R.string.biometric_lock_logout_action`) : si l'empreinte
     * échoue de façon répétée ou irrécupérable, l'utilisateur doit TOUJOURS pouvoir revenir à
     * Connexion et utiliser son mot de passe — voir la doctrine de [com.arzikina.ne.domain.repository.BiometricAuthenticator],
     * "en complément d'une session active", jamais un blocage total de l'application.
     */
    fun onLogout() {
        viewModelScope.launch {
            sessionManager.clearSession()
            _events.emit(BiometricLockEvent.LoggedOut)
        }
    }
}
