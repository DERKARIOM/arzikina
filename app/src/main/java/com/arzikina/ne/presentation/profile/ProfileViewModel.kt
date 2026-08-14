package com.arzikina.ne.presentation.profile

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.AuthResult
import com.arzikina.ne.domain.repository.AuthRepository
import com.arzikina.ne.domain.repository.BiometricAuthenticator
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.UserPreferencesRepository
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
 * État de l'écran Profil. Chargé UNE SEULE FOIS au démarrage (comme
 * `AccountFormViewModel`/`BudgetFormViewModel`, voir leur `init {}`) plutôt
 * qu'observé en continu via [AuthRepository.observeUser] : une source
 * réactive écraserait les modifications en cours de saisie dès que
 * l'utilisateur enregistre (le flux réémettrait la nouvelle valeur pendant
 * qu'il retape autre chose).
 *
 * [username] est affiché mais jamais éditable ici (voir [AuthRepository.updateProfile],
 * qui ne l'accepte pas en paramètre) — changer d'identifiant n'est pas dans
 * le périmètre de cette étape.
 */
data class ProfileFormState(
    val username: String = "",
    val fullName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val profilePhotoUri: String? = null,
    @StringRes val fullNameError: Int? = null,
    @StringRes val emailError: Int? = null,
    val isSaving: Boolean = false
)

sealed interface ProfileEvent {
    data object Saved : ProfileEvent
    data object LoggedOut : ProfileEvent
    data class ShowError(@StringRes val messageRes: Int) : ProfileEvent
}

/**
 * [isAvailable] résolu UNE SEULE FOIS au chargement de l'écran (voir [init]) — contrairement à
 * [isEnabled], qui reste observé en continu (voir [UserPreferencesRepository.observePreferences]) :
 * le matériel biométrique de l'appareil ne change pas pendant qu'un utilisateur reste sur cet
 * écran, alors que le réglage lui-même doit rester réactif comme le reste des préférences de l'app
 * (voir [com.arzikina.ne.presentation.settings.SettingsViewModel] pour le même principe).
 */
data class BiometricLockUiState(
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val biometricAuthenticator: BiometricAuthenticator
) : ViewModel() {

    private val _formState = MutableStateFlow(ProfileFormState())
    val formState: StateFlow<ProfileFormState> = _formState.asStateFlow()

    private val _biometricLockState = MutableStateFlow(BiometricLockUiState())
    val biometricLockState: StateFlow<BiometricLockUiState> = _biometricLockState.asStateFlow()

    private val _events = MutableSharedFlow<ProfileEvent>()
    val events: SharedFlow<ProfileEvent> = _events.asSharedFlow()

    /** Résolu une fois au chargement (voir [init]) — Profil n'édite jamais qu'"soi-même". */
    private var userId: Long = 0L

    init {
        viewModelScope.launch {
            val currentUserId = sessionManager.getCurrentUserIdOnce() ?: return@launch
            userId = currentUserId
            authRepository.getUser(currentUserId)?.let { user ->
                _formState.update {
                    it.copy(
                        username = user.username,
                        fullName = user.fullName,
                        email = user.email,
                        phoneNumber = user.phoneNumber.orEmpty(),
                        profilePhotoUri = user.profilePhotoUri
                    )
                }
            }
        }
        viewModelScope.launch {
            val available = biometricAuthenticator.isAvailable()
            _biometricLockState.update { it.copy(isAvailable = available) }
        }
        viewModelScope.launch {
            userPreferencesRepository.observePreferences().collect { preferences ->
                _biometricLockState.update { it.copy(isEnabled = preferences.biometricLockEnabled) }
            }
        }
    }

    /**
     * Aucune vérification biométrique n'est demandée pour ACTIVER ou DÉSACTIVER ce réglage
     * lui-même (contrairement à son usage une fois activé) : la session locale déjà active suffit
     * à prouver que la personne devant l'appareil est autorisée à changer ses propres préférences
     * — voir la doctrine de [BiometricAuthenticator], "en complément d'une session déjà active".
     */
    fun onBiometricLockToggle(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBiometricLockEnabled(enabled)
        }
    }

    fun onFullNameChange(value: String) {
        _formState.update { it.copy(fullName = value, fullNameError = null) }
    }

    fun onEmailChange(value: String) {
        _formState.update { it.copy(email = value, emailError = null) }
    }

    fun onPhoneNumberChange(value: String) {
        _formState.update { it.copy(phoneNumber = value) }
    }

    fun onProfilePhotoPicked(uri: String?) {
        _formState.update { it.copy(profilePhotoUri = uri) }
    }

    fun save() {
        if (_formState.value.isSaving) return
        if (!validateFormat()) return

        val state = _formState.value
        _formState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = authRepository.updateProfile(
                userId = userId,
                fullName = state.fullName.trim(),
                email = state.email.trim(),
                phoneNumber = state.phoneNumber.trim().ifBlank { null },
                profilePhotoUri = state.profilePhotoUri
            )
            when (result) {
                is AuthResult.Success -> {
                    _formState.update { it.copy(isSaving = false) }
                    _events.emit(ProfileEvent.Saved)
                }
                is AuthResult.Failure -> {
                    _formState.update { it.copy(isSaving = false) }
                    // EmailAlreadyExists est la seule erreur plausible ici (le format est
                    // déjà revalidé côté client) : ciblée sur le champ concerné, comme à
                    // l'inscription.
                    _formState.update { it.copy(emailError = R.string.register_error_email_taken) }
                }
            }
        }
    }

    /**
     * Ne fait QUE nettoyer la session locale ([SessionManager.clearSession]) :
     * aucune donnée de l'utilisateur (comptes, transactions...) n'est
     * touchée — voir les instructions du projet ("la déconnexion ne doit
     * jamais effacer les données").
     */
    fun logout() {
        viewModelScope.launch {
            sessionManager.clearSession()
            _events.emit(ProfileEvent.LoggedOut)
        }
    }

    private fun validateFormat(): Boolean {
        val state = _formState.value
        val fullNameError = if (state.fullName.isBlank()) R.string.register_error_required_field else null
        val emailError = when {
            state.email.isBlank() -> R.string.register_error_required_field
            !AuthValidator.isValidEmail(state.email) -> R.string.register_error_invalid_email
            else -> null
        }
        _formState.update { it.copy(fullNameError = fullNameError, emailError = emailError) }
        return fullNameError == null && emailError == null
    }
}
