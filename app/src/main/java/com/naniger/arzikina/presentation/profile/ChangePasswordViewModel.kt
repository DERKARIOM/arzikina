package com.naniger.arzikina.presentation.profile

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.PasswordChangeError
import com.naniger.arzikina.domain.model.PasswordChangeResult
import com.naniger.arzikina.domain.model.PasswordLimits
import com.naniger.arzikina.domain.model.PasswordProof
import com.naniger.arzikina.domain.repository.UnifiedAuthRepository
import com.naniger.arzikina.util.AuthValidator
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

/** Comment l'utilisateur prouve son identité pour changer (ou définir) son mot de passe. */
enum class ChangePasswordMode {
    /** Il connaît son mot de passe actuel. */
    CURRENT_PASSWORD,

    /** Compte Google sans mot de passe connu : confirmation par Google (voir [PasswordProof.GoogleIdToken]). */
    GOOGLE
}

data class ChangePasswordFormState(
    val mode: ChangePasswordMode = ChangePasswordMode.CURRENT_PASSWORD,
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

    /** Mode [ChangePasswordMode.GOOGLE] : le Fragment ouvre le sélecteur Google puis appelle [ChangePasswordViewModel.onGoogleIdToken]. */
    data object RequestGoogleConfirmation : ChangePasswordEvent
}

/**
 * Écran « Changer le mot de passe ». Passe par [UnifiedAuthRepository.changePassword] : pour un compte synchronisé
 * le changement est fait sur le SERVEUR (puis en local), sinon le mot de passe de ce compte serait refusé sur
 * les autres appareils. Deux preuves possibles (voir [ChangePasswordMode]) ; le ViewModel ne connaît pas
 * `Context` — c'est le Fragment qui obtient le jeton Google.
 */
@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val unifiedAuthRepository: UnifiedAuthRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(ChangePasswordFormState())
    val formState: StateFlow<ChangePasswordFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<ChangePasswordEvent>()
    val events: SharedFlow<ChangePasswordEvent> = _events.asSharedFlow()

    fun onModeChange(mode: ChangePasswordMode) {
        _formState.update { it.copy(mode = mode, currentPassword = "", currentPasswordError = null) }
    }

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

        when (state.mode) {
            ChangePasswordMode.CURRENT_PASSWORD -> change(PasswordProof.CurrentPassword(state.currentPassword))
            ChangePasswordMode.GOOGLE -> viewModelScope.launch { _events.emit(ChangePasswordEvent.RequestGoogleConfirmation) }
        }
    }

    /** Appelé par le Fragment avec le jeton d'identité Google obtenu après [ChangePasswordEvent.RequestGoogleConfirmation]. */
    fun onGoogleIdToken(idToken: String) {
        if (_formState.value.isSubmitting) return
        change(PasswordProof.GoogleIdToken(idToken))
    }

    /** Le sélecteur Google a échoué (fermer la fenêtre n'est PAS une erreur : le Fragment n'appelle rien). */
    fun onGoogleSignInFailed() {
        viewModelScope.launch { _events.emit(ChangePasswordEvent.ShowError(R.string.login_error_google_unavailable)) }
    }

    private fun change(proof: PasswordProof) {
        _formState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = unifiedAuthRepository.changePassword(proof, _formState.value.newPassword)
            _formState.update { it.copy(isSubmitting = false) }
            when (result) {
                is PasswordChangeResult.Success -> _events.emit(ChangePasswordEvent.Changed)
                is PasswordChangeResult.Failure -> handleFailure(result.error)
            }
        }
    }

    private fun validateFormat(): Boolean {
        val state = _formState.value
        val currentPasswordError =
            if (state.mode == ChangePasswordMode.CURRENT_PASSWORD && state.currentPassword.isBlank()) {
                R.string.register_error_required_field
            } else {
                null
            }
        val newPasswordError = when {
            state.newPassword.isBlank() -> R.string.register_error_required_field
            !AuthValidator.isPasswordLongEnough(state.newPassword) -> R.string.register_error_password_too_short
            !PasswordLimits.isWithinMax(state.newPassword) -> R.string.change_password_error_password_too_long
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

    private suspend fun handleFailure(error: PasswordChangeError) {
        when (error) {
            PasswordChangeError.IncorrectCurrentPassword ->
                _formState.update { it.copy(currentPasswordError = R.string.profile_error_current_password_incorrect) }
            PasswordChangeError.InvalidNewPassword ->
                _formState.update { it.copy(newPasswordError = R.string.register_error_password_too_short) }
            PasswordChangeError.GoogleIdentityMismatch ->
                _events.emit(ChangePasswordEvent.ShowError(R.string.change_password_error_google_mismatch))
            PasswordChangeError.InvalidGoogleToken ->
                _events.emit(ChangePasswordEvent.ShowError(R.string.login_error_google_unavailable))
            PasswordChangeError.NoActiveSession ->
                _events.emit(ChangePasswordEvent.ShowError(R.string.change_password_error_no_session))
            PasswordChangeError.NetworkUnavailable ->
                _events.emit(ChangePasswordEvent.ShowError(R.string.change_password_error_network))
            is PasswordChangeError.ServerError, is PasswordChangeError.Unknown ->
                _events.emit(ChangePasswordEvent.ShowError(R.string.change_password_error_server))
        }
    }
}
