package com.arzikina.ne.presentation.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.AuthError
import com.arzikina.ne.domain.model.AuthResult
import com.arzikina.ne.domain.repository.AuthRepository
import com.arzikina.ne.domain.repository.SessionManager
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
 * État du formulaire de Connexion. [formError] est volontairement unique et
 * non rattaché à un champ précis (contrairement à Inscription) : un identifiant
 * ou un mot de passe incorrect ne doit jamais indiquer LEQUEL des deux est en
 * cause (voir la KDoc de `login_error_invalid_credentials` dans strings.xml).
 */
data class LoginFormState(
    val identifier: String = "",
    val password: String = "",
    @StringRes val identifierError: Int? = null,
    @StringRes val passwordError: Int? = null,
    @StringRes val formError: Int? = null,
    val isSubmitting: Boolean = false
)

sealed interface LoginEvent {
    data object LoggedIn : LoginEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _formState = MutableStateFlow(LoginFormState())
    val formState: StateFlow<LoginFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>()
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

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
            when (val result = authRepository.login(state.identifier.trim(), state.password)) {
                is AuthResult.Success -> {
                    sessionManager.startSession(result.data.id)
                    _formState.update { it.copy(isSubmitting = false) }
                    _events.emit(LoginEvent.LoggedIn)
                }
                is AuthResult.Failure -> {
                    _formState.update { it.copy(isSubmitting = false) }
                    handleFailure(result.error)
                }
            }
        }
    }

    private fun validateFormat(): Boolean {
        val state = _formState.value
        // Champ requis générique : réutilise volontairement la même chaîne
        // qu'Inscription (même module Authentification, même message).
        val identifierError = if (state.identifier.isBlank()) R.string.register_error_required_field else null
        val passwordError = if (state.password.isBlank()) R.string.register_error_required_field else null

        _formState.update { it.copy(identifierError = identifierError, passwordError = passwordError, formError = null) }
        return identifierError == null && passwordError == null
    }

    private fun handleFailure(error: AuthError) {
        when (error) {
            AuthError.InvalidCredentials ->
                _formState.update { it.copy(formError = R.string.login_error_invalid_credentials) }
            else ->
                // UserNotFound/CurrentPasswordIncorrect/ValidationFailed/Unknown ne
                // sont normalement pas retournés par login() ; message générique
                // par sécurité si l'implémentation évolue.
                _formState.update { it.copy(formError = R.string.register_error_unknown) }
        }
    }
}
