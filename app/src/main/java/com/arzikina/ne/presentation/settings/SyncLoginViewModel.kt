package com.arzikina.ne.presentation.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.SyncAuthError
import com.arzikina.ne.domain.model.SyncAuthResult
import com.arzikina.ne.domain.repository.SyncAuthRepository
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

/**
 * État du formulaire de connexion au serveur de synchronisation. Même forme que
 * [com.arzikina.ne.presentation.auth.LoginFormState] (authentification LOCALE) — [formError]
 * volontairement unique et non rattaché à un champ précis pour [SyncAuthError.InvalidCredentials],
 * même raisonnement que là-bas — mais distinct dans son propre fichier : les deux écrans ne
 * partagent ni repository ni hiérarchie d'erreurs (voir la KDoc de [SyncAuthError]).
 */
data class SyncLoginFormState(
    val identifier: String = "",
    val password: String = "",
    @StringRes val identifierError: Int? = null,
    @StringRes val passwordError: Int? = null,
    @StringRes val formError: Int? = null,
    val isSubmitting: Boolean = false
)

sealed interface SyncLoginEvent {
    data object LoggedIn : SyncLoginEvent
}

@HiltViewModel
class SyncLoginViewModel @Inject constructor(
    private val syncAuthRepository: SyncAuthRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(SyncLoginFormState())
    val formState: StateFlow<SyncLoginFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<SyncLoginEvent>()
    val events: SharedFlow<SyncLoginEvent> = _events.asSharedFlow()

    fun onIdentifierChange(value: String) {
        _formState.update { it.copy(identifier = value, identifierError = null, formError = null) }
    }

    fun onPasswordChange(value: String) {
        _formState.update { it.copy(password = value, passwordError = null, formError = null) }
    }

    fun submit() {
        if (_formState.value.isSubmitting) return
        if (!validateFormat()) return

        val state = _formState.value
        _formState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            when (val result = syncAuthRepository.login(state.identifier.trim(), state.password)) {
                is SyncAuthResult.Success -> {
                    _formState.update { it.copy(isSubmitting = false) }
                    _events.emit(SyncLoginEvent.LoggedIn)
                }
                is SyncAuthResult.Failure -> {
                    _formState.update { it.copy(isSubmitting = false) }
                    handleFailure(result.error)
                }
            }
        }
    }

    private fun validateFormat(): Boolean {
        val state = _formState.value
        // Réutilise volontairement la même chaîne générique que l'authentification locale
        // (register_error_required_field) : même module Authentification, même message.
        val identifierError = if (state.identifier.isBlank()) R.string.register_error_required_field else null
        val passwordError = if (state.password.isBlank()) R.string.register_error_required_field else null

        _formState.update { it.copy(identifierError = identifierError, passwordError = passwordError, formError = null) }
        return identifierError == null && passwordError == null
    }

    private fun handleFailure(error: SyncAuthError) {
        val messageRes = when (error) {
            SyncAuthError.InvalidCredentials -> R.string.sync_login_error_invalid_credentials
            SyncAuthError.NetworkUnavailable -> R.string.sync_login_error_network
            is SyncAuthError.ServerError -> R.string.sync_login_error_server
            is SyncAuthError.Unknown -> R.string.sync_login_error_server
        }
        _formState.update { it.copy(formError = messageRes) }
    }
}
