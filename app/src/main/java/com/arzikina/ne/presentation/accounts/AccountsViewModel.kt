package com.arzikina.ne.presentation.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.AuthRepository
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.TransactionRepository
import com.arzikina.ne.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * État affiché par l'écran "Mes comptes". Plus de solde total agrégé ici
 * (bloc retiré de l'écran) : chaque compte affiche déjà son propre solde sur
 * sa carte (voir [AccountUiItem]/`item_account.xml`).
 */
data class AccountsUiState(
    val accounts: List<AccountUiItem>
)

/**
 * État et actions de l'écran "Liste des comptes".
 */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
    authRepository: AuthRepository,
    sessionManager: SessionManager
) : ViewModel() {

    val uiState: StateFlow<AppResult<AccountsUiState>> = combine(
        accountRepository.observeAccounts(),
        transactionRepository.observeTransactions(),
        // Nom du titulaire affiché sur une carte de crédit (voir AccountUiItem.cardHolderName) :
        // même source que la carte VISA du Dashboard, pas un champ propre au compte.
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) flowOf(null) else authRepository.observeUser(userId)
        }
    ) { accounts, transactions, user ->
        val currentBalances = computeCurrentBalances(accounts, transactions)
        val cardHolderName = user?.fullName.orEmpty()
        val items = accounts.map { account ->
            AccountUiItem(
                account = account,
                currentBalance = currentBalances[account.id] ?: account.initialBalance,
                cardHolderName = cardHolderName
            )
        }

        AccountsUiState(accounts = items)
    }
        .map<AccountsUiState, AppResult<AccountsUiState>> { AppResult.Success(it) }
        .catch { throwable -> emit(AppResult.Error(throwable.message ?: "Erreur inconnue", throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )
}
