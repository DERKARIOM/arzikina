package com.arzikina.ne.presentation.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.AuthError
import com.arzikina.ne.domain.model.AuthResult
import com.arzikina.ne.domain.model.SecurityQuestion
import com.arzikina.ne.domain.repository.AuthRepository
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

/**
 * Assistant en 2 étapes, tenu dans un seul état plutôt que dans deux écrans
 * séparés avec argument de navigation : évite de faire transiter
 * l'identifiant (ou pire, un état "identité déjà vérifiée") via le
 * back-stack de Navigation, ce qui serait fragile en cas de recréation du
 * Fragment (rotation, process death). [step] pilote uniquement l'affichage
 * (voir ForgotPasswordFragment) ; la vérification réelle reste faite en un
 * seul appel atomique par [AuthRepository.resetPasswordWithSecurityAnswer].
 */
enum class ForgotPasswordStep { IDENTIFIER, RESET }

data class ForgotPasswordFormState(
    val step: ForgotPasswordStep = ForgotPasswordStep.IDENTIFIER,
    val identifier: String = "",
    @StringRes val identifierError: Int? = null,
    val securityQuestion: SecurityQuestion? = null,
    val securityAnswer: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    @StringRes val securityAnswerError: Int? = null,
    @StringRes val newPasswordError: Int? = null,
    @StringRes val confirmPasswordError: Int? = null,
    val isSubmitting: Boolean = false
)

sealed interface ForgotPasswordEvent {
    data object PasswordReset : ForgotPasswordEvent
    data class ShowError(@StringRes val messageRes: Int) : ForgotPasswordEvent
}

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(ForgotPasswordFormState())
    val formState: StateFlow<ForgotPasswordFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<ForgotPasswordEvent>()
    val events: SharedFlow<ForgotPasswordEvent> = _events.asSharedFlow()

    fun onIdentifierChange(value: String) {
        _formState.update { it.copy(identifier = value, identifierError = null) }
    }

    fun onSecurityAnswerChange(value: String) {
        _formState.update { it.copy(securityAnswer = value, securityAnswerError = null) }
    }

    fun onNewPasswordChange(value: String) {
        _formState.update {
            it.copy(newPassword = value, newPasswordError = null, confirmPasswordError = null)
        }
    }

    fun onConfirmPasswordChange(value: String) {
        _formState.update { it.copy(confirmPassword = value, confirmPasswordError = null) }
    }

    /**
     * Étape 1 : ne fait QUE récupérer la question associée à [identifier]
     * (voir [AuthRepository.getSecurityQuestion]). Aucun mot de passe, aucune
     * réponse manipulés ici — seule l'étape 2 peut effectuer la
     * réinitialisation elle-même.
     */
    fun submitIdentifier() {
        val state = _formState.value
        if (state.isSubmitting) return
        if (state.identifier.isBlank()) {
            _formState.update { it.copy(identifierError = R.string.register_error_required_field) }
            return
        }

        _formState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val question = authRepository.getSecurityQuestion(state.identifier.trim())
            if (question == null) {
                _formState.update { it.copy(isSubmitting = false) }
                _events.emit(ForgotPasswordEvent.ShowError(R.string.forgot_password_error_user_not_found))
                return@launch
            }
            _formState.update {
                it.copy(isSubmitting = false, step = ForgotPasswordStep.RESET, securityQuestion = question)
            }
        }
    }

    fun submitReset() {
        val state = _formState.value
        if (state.isSubmitting) return
        if (!validateResetFormat()) return

        _formState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = authRepository.resetPasswordWithSecurityAnswer(
                identifier = state.identifier.trim(),
                securityAnswer = state.securityAnswer.trim(),
                newRawPassword = state.newPassword
            )
            when (result) {
                is AuthResult.Success -> {
                    _formState.update { it.copy(isSubmitting = false) }
                    _events.emit(ForgotPasswordEvent.PasswordReset)
                }
                is AuthResult.Failure -> {
                    _formState.update { it.copy(isSubmitting = false) }
                    handleFailure(result.error)
                }
            }
        }
    }

    private fun validateResetFormat(): Boolean {
        val state = _formState.value
        val securityAnswerError = if (state.securityAnswer.isBlank()) {
            R.string.register_error_required_field
        } else {
            null
        }
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
                securityAnswerError = securityAnswerError,
                newPasswordError = newPasswordError,
                confirmPasswordError = confirmPasswordError
            )
        }
        return listOf(securityAnswerError, newPasswordError, confirmPasswordError).all { it == null }
    }

    private suspend fun handleFailure(error: AuthError) {
        when (error) {
            AuthError.SecurityAnswerIncorrect ->
                _formState.update { it.copy(securityAnswerError = R.string.forgot_password_error_answer_incorrect) }
            else ->
                // UserNotFound ne devrait plus arriver ici (déjà vérifié à l'étape 1) ;
                // les autres cas (ValidationFailed, Unknown...) restent génériques.
                _events.emit(ForgotPasswordEvent.ShowError(R.string.register_error_unknown))
        }
    }
}
