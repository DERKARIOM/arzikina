package com.arzikina.ne.presentation.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.UnifiedAuthError
import com.arzikina.ne.domain.model.UnifiedAuthResult
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.SyncEngine
import com.arzikina.ne.domain.repository.UnifiedAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
 * Étape affichée pendant [LoginViewModel.submit] (cahier des charges "audit auth + sync +
 * doublons", section UX de connexion) : [AUTHENTICATING] pendant l'appel à
 * [UnifiedAuthRepository.login], [SYNCING] pendant l'enchaînement post-connexion de
 * [LoginViewModel.runPostLoginSync], puis un message final bref ([SYNC_DONE]/[SYNC_FAILED]/
 * [OFFLINE]) avant [LoginEvent.LoggedIn] — voir [messageRes] pour le texte associé à chaque valeur.
 */
enum class LoginStage { IDLE, AUTHENTICATING, SYNCING, SYNC_DONE, SYNC_FAILED, OFFLINE }

/** `null` pour [IDLE] (rien à afficher, formulaire au repos). */
@StringRes
fun LoginStage.messageRes(): Int? = when (this) {
    LoginStage.IDLE -> null
    LoginStage.AUTHENTICATING -> R.string.login_stage_authenticating
    LoginStage.SYNCING -> R.string.login_stage_syncing
    LoginStage.SYNC_DONE -> R.string.login_stage_sync_done
    LoginStage.SYNC_FAILED -> R.string.login_stage_sync_failed
    LoginStage.OFFLINE -> R.string.login_stage_offline
}

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
    val stage: LoginStage = LoginStage.IDLE
) {
    /** Dérivé de [stage] plutôt que stocké séparément : un seul état à tenir cohérent. */
    val isSubmitting: Boolean get() = stage != LoginStage.IDLE
}

sealed interface LoginEvent {
    data object LoggedIn : LoginEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val unifiedAuthRepository: UnifiedAuthRepository,
    private val sessionManager: SessionManager,
    private val syncEngine: SyncEngine
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

    /**
     * Point d'entrée UNIQUE de l'app (voir la KDoc de [LoginFragment]) : passe désormais par
     * [UnifiedAuthRepository] plutôt que par l'ancien [com.arzikina.ne.domain.repository.AuthRepository]
     * local — un compte inscrit localement (ancien écran d'inscription, inchangé) continue de
     * fonctionner ici : [UnifiedAuthRepository.login] tente une migration serveur SILENCIEUSE dès
     * la première connexion (voir `UnifiedAuthRepositoryImpl.attemptSilentServerMigration`), aucune
     * action supplémentaire n'est nécessaire de ce côté.
     */
    fun submit() {
        if (_formState.value.isSubmitting) return
        if (!validateFormat()) return

        val state = _formState.value
        _formState.update { it.copy(stage = LoginStage.AUTHENTICATING) }
        viewModelScope.launch {
            when (val result = unifiedAuthRepository.login(state.identifier.trim(), state.password)) {
                is UnifiedAuthResult.Success -> onLoginSuccess(result)
                is UnifiedAuthResult.Failure -> {
                    _formState.update { it.copy(stage = LoginStage.IDLE) }
                    handleFailure(result.error)
                }
            }
        }
    }

    /**
     * Démarre la session locale puis, hors repli hors-ligne (voir
     * [UnifiedAuthResult.Success.usedLocalFallback]), enchaîne la synchronisation complète. Le
     * message final ([LoginStage.SYNC_DONE]/[SYNC_FAILED]/[OFFLINE]) reste affiché brièvement
     * ([STAGE_MESSAGE_DELAY_MS]) avant [LoginEvent.LoggedIn] : conforme au cahier des charges qui
     * demande explicitement un message "✓ Synchronisation terminée" visible avant d'entrer dans
     * l'app, pas une simple redirection instantanée.
     */
    private suspend fun onLoginSuccess(result: UnifiedAuthResult.Success) {
        sessionManager.startSession(result.localUserId)

        if (result.usedLocalFallback) {
            _formState.update { it.copy(stage = LoginStage.OFFLINE) }
            delay(STAGE_MESSAGE_DELAY_MS)
            _events.emit(LoginEvent.LoggedIn)
            return
        }

        _formState.update { it.copy(stage = LoginStage.SYNCING) }
        val syncSucceeded = runPostLoginSync()
        _formState.update { it.copy(stage = if (syncSucceeded) LoginStage.SYNC_DONE else LoginStage.SYNC_FAILED) }
        delay(STAGE_MESSAGE_DELAY_MS)
        _events.emit(LoginEvent.LoggedIn)
    }

    /**
     * Ordre volontairement PULL → [SyncEngine.enqueueUnsyncedLocalData] → PUSH, DIFFÉRENT de
     * l'ordre push-puis-pull du bouton "Synchroniser maintenant"
     * ([com.arzikina.ne.presentation.components.SyncButtonController.syncNow]) : ce premier
     * enchaînement suivant une connexion doit voir l'état SERVEUR avant de mettre en file les
     * données locales jamais synchronisées, sinon le rattachement par nom/type de l'étape A
     * (`CategoryDao.getUnsyncedByNameAndType`/`AccountDao.getUnsyncedByNameAndType`) n'a rien à
     * quoi se raccrocher et les catégories/comptes système par défaut repartent en double —
     * exactement le bug déjà corrigé et nettoyé (étapes A/B de ce chantier). N'échoue jamais
     * bruyamment envers l'appelant (voir [runCatching] ci-dessous, même idiome que
     * [com.arzikina.ne.presentation.components.SyncButtonController.syncNow]) : une erreur ici
     * n'empêche JAMAIS l'entrée dans l'app, l'utilisateur est déjà authentifié (voir
     * [LoginStage.SYNC_FAILED], cahier des charges section 13 sur la poursuite possible malgré un
     * échec de synchronisation).
     */
    private suspend fun runPostLoginSync(): Boolean = runCatching {
        syncEngine.pullRemoteChanges()
        syncEngine.enqueueUnsyncedLocalData()
        syncEngine.pushPendingChanges()
    }.isSuccess

    private fun validateFormat(): Boolean {
        val state = _formState.value
        // Champ requis générique : réutilise volontairement la même chaîne
        // qu'Inscription (même module Authentification, même message).
        val identifierError = if (state.identifier.isBlank()) R.string.register_error_required_field else null
        val passwordError = if (state.password.isBlank()) R.string.register_error_required_field else null

        _formState.update { it.copy(identifierError = identifierError, passwordError = passwordError, formError = null) }
        return identifierError == null && passwordError == null
    }

    private fun handleFailure(error: UnifiedAuthError) {
        val messageRes = when (error) {
            UnifiedAuthError.InvalidCredentials -> R.string.login_error_invalid_credentials
            UnifiedAuthError.NetworkUnavailableNoLocalFallback -> R.string.sync_login_error_network
            is UnifiedAuthError.ServerError -> R.string.sync_login_error_server
            is UnifiedAuthError.Unknown -> R.string.sync_login_error_server
            is UnifiedAuthError.ValidationFailed -> R.string.register_error_required_field
            // Réservé à une inscription volontaire (voir UnifiedAuthRepository.register) — jamais
            // renvoyée par login() en pratique ; filet de sécurité pour rester exhaustif.
            UnifiedAuthError.EmailAlreadyExists -> R.string.register_error_email_taken
        }
        _formState.update { it.copy(formError = messageRes) }
    }

    private companion object {
        /** Durée d'affichage du message final (voir [onLoginSuccess]) avant navigation — assez
         *  court pour ne pas ralentir la connexion, assez long pour être lisible. */
        const val STAGE_MESSAGE_DELAY_MS = 550L
    }
}
