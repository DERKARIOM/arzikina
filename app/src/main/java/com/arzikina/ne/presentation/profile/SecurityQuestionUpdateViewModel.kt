package com.arzikina.ne.presentation.profile

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.AuthError
import com.arzikina.ne.domain.model.AuthResult
import com.arzikina.ne.domain.model.SecurityQuestion
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

data class SecurityQuestionUpdateFormState(
    val currentPassword: String = "",
    val securityQuestion: SecurityQuestion = SecurityQuestion.FIRST_PET_NAME,
    val securityAnswer: String = "",
    @StringRes val currentPasswordError: Int? = null,
    @StringRes val securityAnswerError: Int? = null,
    val isSubmitting: Boolean = false
)

sealed interface SecurityQuestionUpdateEvent {
    data object Updated : SecurityQuestionUpdateEvent
    data class ShowError(@StringRes val messageRes: Int) : SecurityQuestionUpdateEvent
}

/**
 * Permet de définir/modifier la question de sécurité depuis le Profil — en
 * particulier pour l'utilisateur "par défaut" créé par une migration
 * antérieure à ce mécanisme (voir `Migration7To8`, `securityAnswerHash`
 * vide) qui, sans cet écran, ne pourrait JAMAIS utiliser "Mot de passe
 * oublié".
 */
@HiltViewModel
class SecurityQuestionUpdateViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _formState = MutableStateFlow(SecurityQuestionUpdateFormState())
    val formState: StateFlow<SecurityQuestionUpdateFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<SecurityQuestionUpdateEvent>()
    val events: SharedFlow<SecurityQuestionUpdateEvent> = _events.asSharedFlow()

    fun onCurrentPasswordChange(value: String) {
        _formState.update { it.copy(currentPassword = value, currentPasswordError = null) }
    }

    fun onSecurityQuestionChange(value: SecurityQuestion) {
        _formState.update { it.copy(securityQuestion = value) }
    }

    fun onSecurityAnswerChange(value: String) {
        _formState.update { it.copy(securityAnswer = value, securityAnswerError = null) }
    }

    fun submit() {
        val state = _formState.value
        if (state.isSubmitting) return
        if (state.currentPassword.isBlank() || state.securityAnswer.isBlank()) {
            _formState.update {
                it.copy(
                    currentPasswordError = if (state.currentPassword.isBlank()) {
                        R.string.register_error_required_field
                    } else {
                        null
                    },
                    securityAnswerError = if (state.securityAnswer.isBlank()) {
                        R.string.register_error_required_field
                    } else {
                        null
                    }
                )
            }
            return
        }

        _formState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val userId = sessionManager.getCurrentUserIdOnce()
            if (userId == null) {
                _formState.update { it.copy(isSubmitting = false) }
                _events.emit(SecurityQuestionUpdateEvent.ShowError(R.string.register_error_unknown))
                return@launch
            }
            val result = authRepository.updateSecurityQuestion(
                userId = userId,
                currentRawPassword = state.currentPassword,
                securityQuestion = state.securityQuestion,
                securityAnswer = state.securityAnswer.trim()
            )
            when (result) {
                is AuthResult.Success -> {
                    _formState.update { it.copy(isSubmitting = false) }
                    _events.emit(SecurityQuestionUpdateEvent.Updated)
                }
                is AuthResult.Failure -> {
                    _formState.update { it.copy(isSubmitting = false) }
                    handleFailure(result.error)
                }
            }
        }
    }

    private suspend fun handleFailure(error: AuthError) {
        when (error) {
            AuthError.CurrentPasswordIncorrect ->
                _formState.update {
                    it.copy(currentPasswordError = R.string.profile_error_current_password_incorrect)
                }
            AuthError.ValidationFailed(AuthError.ValidationReason.SECURITY_ANSWER_TOO_SHORT) ->
                _formState.update {
                    it.copy(securityAnswerError = R.string.register_error_security_answer_too_short)
                }
            else -> _events.emit(SecurityQuestionUpdateEvent.ShowError(R.string.register_error_unknown))
        }
    }
}
