package com.arzikina.ne.presentation.profile

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.AuthError
import com.arzikina.ne.domain.model.AuthResult
import com.arzikina.ne.domain.repository.AuthRepository
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.util.AuthValidator
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

data class ChangePasswordFormState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    @StringRes val currentPasswordError: Int? = null,
    @StringRes val newPasswordError: Int? = null,
    @StringRes val confirmPasswordError: Int? = null,
    val isSubmitting: Boolean = false
)

sealed interface ChangePasswordEvent {
    data object Changed : ChangePasswordEvent
    data class ShowError(@StringRes val messageRes: Int) : ChangePasswordEvent
}

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _formState = MutableStateFlow(ChangePasswordFormState())
    val formState: StateFlow<ChangePasswordFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<ChangePasswordEvent>()
    val events: SharedFlow<ChangePasswordEvent> = _events.asSharedFlow()

    fun onCurrentPasswordChange(value: String) {
        _formState.update { it.copy(currentPassword = value, currentPasswordError = null) }
    }

    fun onNewPasswordChange(value: String) {
        _formState.update {
            it.copy(newPassword = value, newPasswordError = null, confirmPasswordError = null)
        }
    }

    fun onConfirmPasswordChange(value: String) {
        _formState.update { it.copy(confirmPassword = value, confirmPasswordError = null) }
    }

    fun submit() {
        val state = _formState.value
        if (state.isSubmitting) return
        if (!validateFormat()) return

        _formState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val userId = sessionManager.getCurrentUserIdOnce()
            if (userId == null) {
                _formState.update { it.copy(isSubmitting = false) }
                _events.emit(ChangePasswordEvent.ShowError(R.string.register_error_unknown))
                return@launch
            }
            val result = authRepository.changePassword(
                userId = userId,
                currentRawPassword = state.currentPassword,
                newRawPassword = state.newPassword
            )
            when (result) {
                is AuthResult.Success -> {
                    _formState.update { it.copy(isSubmitting = false) }
                    _events.emit(ChangePasswordEvent.Changed)
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
        val currentPasswordError =
            if (state.currentPassword.isBlank()) R.string.register_error_required_field else null
        val newPasswordError = when {
            state.newPassword.isBlank() -> R.string.register_error_required_field
            !AuthValidator.isPasswordLongEnough(state.newPassword) -> R.string.register_error_password_too_short
            else -> null
        }
        val confirmPasswordError = if (newPasswordError == null &&
            !AuthValidator.doPasswordsMatch(state.newPassword, state.confirmPassword)
        ) {
            R.string.register_error_passwords_do_not_match
        } else {
            null
        }

        _formState.update {
            it.copy(
                currentPasswordError = currentPasswordError,
                newPasswordError = newPasswordError,
                confirmPasswordError = confirmPasswordError
            )
        }
        return listOf(currentPasswordError, newPasswordError, confirmPasswordError).all { it == null }
    }

    private suspend fun handleFailure(error: AuthError) {
        when (error) {
            AuthError.CurrentPasswordIncorrect ->
                _formState.update {
                    it.copy(currentPasswordError = R.string.profile_error_current_password_incorrect)
                }
            else -> _events.emit(ChangePasswordEvent.ShowError(R.string.register_error_unknown))
        }
    }
}
