package com.naniger.arzikina.presentation.profile

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.AccountDeletionError
import com.naniger.arzikina.domain.model.AccountDeletionResult
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

/**
 * État de l'écran "Supprimer mon compte" (voir [DeleteAccountFragment]) — formulaire à un seul
 * champ (mot de passe), même principe que [ChangePasswordFormState] mais plus simple : aucun
 * nouveau mot de passe à choisir ici, juste une revérification d'identité avant une action
 * IRRÉVERSIBLE (voir [UnifiedAuthRepository.deleteAccount]).
 */
data class DeleteAccountFormState(
    val password: String = "",
    @StringRes val passwordError: Int? = null,
    val isSubmitting: Boolean = false
)

sealed interface DeleteAccountEvent {
    data object Deleted : DeleteAccountEvent
    data class ShowError(@StringRes val messageRes: Int) : DeleteAccountEvent
}

@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val unifiedAuthRepository: UnifiedAuthRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(DeleteAccountFormState())
    val formState: StateFlow<DeleteAccountFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<DeleteAccountEvent>()
    val events: SharedFlow<DeleteAccountEvent> = _events.asSharedFlow()

    fun onPasswordChange(value: String) {
        _formState.update { it.copy(password = value, passwordError = null) }
    }

    /** Le mot de passe re-saisi ici TIENT LIEU de confirmation (voir la doc de tête de
     *  [DeleteAccountFragment]) : aucun double dialogue "Êtes-vous sûr ?" supplémentaire. */
    fun submit() {
        val state = _formState.value
        if (state.isSubmitting) return
        if (state.password.isBlank()) {
            _formState.update { it.copy(passwordError = R.string.register_error_required_field) }
            return
        }

        _formState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            when (val result = unifiedAuthRepository.deleteAccount(state.password)) {
                is AccountDeletionResult.Success -> {
                    _formState.update { it.copy(isSubmitting = false) }
                    _events.emit(DeleteAccountEvent.Deleted)
                }
                is AccountDeletionResult.Failure -> {
                    _formState.update { it.copy(isSubmitting = false) }
                    handleFailure(result.error)
                }
            }
        }
    }

    private suspend fun handleFailure(error: AccountDeletionError) {
        when (error) {
            AccountDeletionError.IncorrectPassword ->
                _formState.update { it.copy(passwordError = R.string.profile_error_current_password_incorrect) }
            AccountDeletionError.NetworkUnavailable ->
                _events.emit(DeleteAccountEvent.ShowError(R.string.delete_account_error_network_unavailable))
            else -> _events.emit(DeleteAccountEvent.ShowError(R.string.register_error_unknown))
        }
    }
}
