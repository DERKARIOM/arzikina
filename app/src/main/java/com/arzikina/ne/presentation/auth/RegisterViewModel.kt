package com.arzikina.ne.presentation.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.AuthError
import com.arzikina.ne.domain.model.AuthResult
import com.arzikina.ne.domain.model.SecurityQuestion
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

/**
 * État du formulaire d'inscription. Les erreurs sont des ID de ressource
 * (`@StringRes`), pas du texte en dur : le domaine ([AuthError]) ne produit
 * jamais de texte affichable (voir sa KDoc), c'est ce ViewModel qui choisit
 * la chaîne localisée correspondante. Rattachées au champ concerné plutôt
 * qu'affichées génériquement : l'utilisateur doit voir immédiatement QUEL
 * champ corriger.
 */
data class RegisterFormState(
    val fullName: String = "",
    val username: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val profilePhotoUri: String? = null,
    /** Toujours une valeur valide (liste FERMÉE, voir [SecurityQuestion]) : jamais "non choisie". */
    val securityQuestion: SecurityQuestion = SecurityQuestion.FIRST_PET_NAME,
    val securityAnswer: String = "",
    @StringRes val fullNameError: Int? = null,
    @StringRes val usernameError: Int? = null,
    @StringRes val emailError: Int? = null,
    @StringRes val passwordError: Int? = null,
    @StringRes val confirmPasswordError: Int? = null,
    @StringRes val securityAnswerError: Int? = null,
    val isSubmitting: Boolean = false
)

sealed interface RegisterEvent {
    data object Registered : RegisterEvent
    data class ShowError(@StringRes val messageRes: Int) : RegisterEvent
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _formState = MutableStateFlow(RegisterFormState())
    val formState: StateFlow<RegisterFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<RegisterEvent>()
    val events: SharedFlow<RegisterEvent> = _events.asSharedFlow()

    fun onFullNameChange(value: String) {
        _formState.update { it.copy(fullName = value, fullNameError = null) }
    }

    fun onUsernameChange(value: String) {
        _formState.update { it.copy(username = value, usernameError = null) }
    }

    fun onEmailChange(value: String) {
        _formState.update { it.copy(email = value, emailError = null) }
    }

    fun onPhoneNumberChange(value: String) {
        _formState.update { it.copy(phoneNumber = value) }
    }

    fun onPasswordChange(value: String) {
        _formState.update { it.copy(password = value, passwordError = null, confirmPasswordError = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _formState.update { it.copy(confirmPassword = value, confirmPasswordError = null) }
    }

    fun onProfilePhotoPicked(uri: String?) {
        _formState.update { it.copy(profilePhotoUri = uri) }
    }

    fun onSecurityQuestionChange(value: SecurityQuestion) {
        _formState.update { it.copy(securityQuestion = value) }
    }

    fun onSecurityAnswerChange(value: String) {
        _formState.update { it.copy(securityAnswer = value, securityAnswerError = null) }
    }

    /**
     * Validation de FORMAT en local (voir [AuthValidator]) avant tout appel
     * au repository : retour instantané, sans aller-retour base de données,
     * pour les erreurs les plus courantes (champ vide, e-mail mal formé...).
     * [AuthRepository.register] revalide de toute façon ces mêmes règles en
     * profondeur (défense en profondeur, voir sa KDoc).
     */
    fun submit() {
        if (_formState.value.isSubmitting) return
        if (!validateFormat()) return

        val state = _formState.value
        _formState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = authRepository.register(
                fullName = state.fullName.trim(),
                username = state.username.trim(),
                email = state.email.trim(),
                phoneNumber = state.phoneNumber.trim().ifBlank { null },
                rawPassword = state.password,
                profilePhotoUri = state.profilePhotoUri,
                securityQuestion = state.securityQuestion,
                securityAnswer = state.securityAnswer.trim()
            )
            when (result) {
                is AuthResult.Success -> {
                    sessionManager.startSession(result.data.id)
                    _formState.update { it.copy(isSubmitting = false) }
                    _events.emit(RegisterEvent.Registered)
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
        val fullNameError = if (state.fullName.isBlank()) R.string.register_error_required_field else null
        val usernameError = when {
            state.username.isBlank() -> R.string.register_error_required_field
            !AuthValidator.isValidUsername(state.username) -> R.string.register_error_invalid_username
            else -> null
        }
        val emailError = when {
            state.email.isBlank() -> R.string.register_error_required_field
            !AuthValidator.isValidEmail(state.email) -> R.string.register_error_invalid_email
            else -> null
        }
        val passwordError = when {
            state.password.isBlank() -> R.string.register_error_required_field
            !AuthValidator.isPasswordLongEnough(state.password) -> R.string.register_error_password_too_short
            else -> null
        }
        val confirmPasswordError = if (passwordError == null &&
            !AuthValidator.doPasswordsMatch(state.password, state.confirmPassword)
        ) {
            R.string.register_error_passwords_do_not_match
        } else {
            null
        }
        val securityAnswerError = when {
            state.securityAnswer.isBlank() -> R.string.register_error_required_field
            !AuthValidator.isSecurityAnswerLongEnough(state.securityAnswer) ->
                R.string.register_error_security_answer_too_short
            else -> null
        }

        _formState.update {
            it.copy(
                fullNameError = fullNameError,
                usernameError = usernameError,
                emailError = emailError,
                passwordError = passwordError,
                confirmPasswordError = confirmPasswordError,
                securityAnswerError = securityAnswerError
            )
        }
        return listOf(
            fullNameError, usernameError, emailError, passwordError, confirmPasswordError, securityAnswerError
        ).all { it == null }
    }

    private suspend fun handleFailure(error: AuthError) {
        when (error) {
            AuthError.UsernameAlreadyExists ->
                _formState.update { it.copy(usernameError = R.string.register_error_username_taken) }
            AuthError.EmailAlreadyExists ->
                _formState.update { it.copy(emailError = R.string.register_error_email_taken) }
            else ->
                // Couvre AuthError.ValidationFailed (ne devrait plus arriver après
                // validateFormat(), voir la KDoc d'AuthRepository), InvalidCredentials,
                // UserNotFound, CurrentPasswordIncorrect (non pertinents pour l'inscription)
                // et Unknown : un message générique suffit, ce ne sont pas des cas
                // qu'un utilisateur peut corriger en changeant un champ précis.
                _events.emit(RegisterEvent.ShowError(R.string.register_error_unknown))
        }
    }
}
