package com.arzikina.ne.presentation.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.AccountIcon
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.util.Constants
import com.arzikina.ne.util.Money
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
 * État du formulaire d'ajout/édition de compte.
 *
 * `Account.id == 0L` fait office de sentinelle "nouveau compte" (même
 * convention que [Account.id] et [AccountRepository.saveAccount]).
 */
data class AccountFormState(
    val name: String = "",
    val icon: AccountIcon = AccountIcon.CASH,
    val colorArgb: Long = 0xFF10B981L,
    val currencyCode: String = Constants.DEFAULT_CURRENCY_CODE,
    val initialBalanceInput: String = "0",
    val createdAt: Long? = null,
    val nameError: String? = null,
    val balanceError: String? = null
)

sealed interface AccountFormEvent {
    data object Saved : AccountFormEvent
    data object Deleted : AccountFormEvent
}

@HiltViewModel
class AccountFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val accountId: Long = savedStateHandle.get<Long>(ACCOUNT_ID_ARG) ?: 0L
    val isEditMode: Boolean = accountId != 0L

    private val _formState = MutableStateFlow(AccountFormState())
    val formState: StateFlow<AccountFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<AccountFormEvent>()
    val events: SharedFlow<AccountFormEvent> = _events.asSharedFlow()

    init {
        if (isEditMode) {
            viewModelScope.launch {
                accountRepository.getAccount(accountId)?.let { account ->
                    _formState.update {
                        it.copy(
                            name = account.name,
                            icon = account.icon,
                            colorArgb = account.colorArgb,
                            currencyCode = account.currencyCode,
                            initialBalanceInput = Money.formatMajorUnits(account.initialBalance),
                            createdAt = account.createdAt
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _formState.update { it.copy(name = value, nameError = null) }
    }

    fun onIconChange(icon: AccountIcon) {
        _formState.update { it.copy(icon = icon) }
    }

    fun onColorChange(colorArgb: Long) {
        _formState.update { it.copy(colorArgb = colorArgb) }
    }

    fun onCurrencyChange(code: String) {
        _formState.update { it.copy(currencyCode = code) }
    }

    fun onInitialBalanceChange(value: String) {
        _formState.update { it.copy(initialBalanceInput = value, balanceError = null) }
    }

    fun save() {
        val state = _formState.value
        val trimmedName = state.name.trim()
        if (trimmedName.isEmpty()) {
            _formState.update { it.copy(nameError = "Le nom est obligatoire") }
            return
        }

        val balanceMinor = Money.parseToMinorUnits(state.initialBalanceInput)
        if (balanceMinor == null) {
            _formState.update { it.copy(balanceError = "Montant invalide") }
            return
        }

        viewModelScope.launch {
            accountRepository.saveAccount(
                Account(
                    id = accountId,
                    name = trimmedName,
                    icon = state.icon,
                    colorArgb = state.colorArgb,
                    currencyCode = state.currencyCode,
                    initialBalance = balanceMinor,
                    createdAt = state.createdAt ?: System.currentTimeMillis()
                )
            )
            _events.emit(AccountFormEvent.Saved)
        }
    }

    fun delete() {
        if (!isEditMode) return
        viewModelScope.launch {
            accountRepository.deleteAccount(accountId)
            _events.emit(AccountFormEvent.Deleted)
        }
    }

    private companion object {
        const val ACCOUNT_ID_ARG = "accountId"
    }
}
