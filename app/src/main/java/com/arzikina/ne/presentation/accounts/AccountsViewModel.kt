package com.arzikina.ne.presentation.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.AccountType
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.AuthRepository
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.TransactionRepository
import com.arzikina.ne.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * Bascule PUREMENT visuelle du ToggleGroup en haut de l'écran (voir `accountsTabGroup`,
 * `fragment_accounts.xml`) — [BANK_CARDS] ne désigne aucune nouvelle entité : c'est un simple
 * filtre d'affichage sur [AccountType.CREDIT_CARD], le seul type déjà rendu comme une carte
 * bancaire visuelle (voir `AccountsAdapter`/`item_account_credit_card.xml`). Tous les autres
 * types (`CASH`, `BANK`, `MOBILE_MONEY`, `SAVINGS`) restent sous [ACCOUNTS].
 */
enum class AccountsDisplayTab { ACCOUNTS, BANK_CARDS }

/** Voir [AccountsDisplayTab] : un compte "correspond" à l'onglet Cartes bancaires si et
 * seulement s'il est de type [AccountType.CREDIT_CARD]. */
fun AccountUiItem.matchesTab(tab: AccountsDisplayTab): Boolean =
    (account.type == AccountType.CREDIT_CARD) == (tab == AccountsDisplayTab.BANK_CARDS)

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

    /**
     * StateFlow SÉPARÉ de [uiState] (même raisonnement que `SettingsViewModel.biometricLockState`,
     * voir sa doc) : purement de l'état d'affichage, non persisté (redémarre toujours sur
     * [AccountsDisplayTab.ACCOUNTS] à chaque nouvelle instance de ce ViewModel), aucun lien avec le
     * `combine` ci-dessous qui charge les données réelles.
     */
    private val _selectedTab = MutableStateFlow(AccountsDisplayTab.ACCOUNTS)
    val selectedTab: StateFlow<AccountsDisplayTab> = _selectedTab.asStateFlow()

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

    /** Voir [AccountsDisplayTab] : appelé par `AccountsFragment` au clic sur `btnAccounts`/
     * `btnBankCards`. */
    fun onTabSelected(tab: AccountsDisplayTab) {
        _selectedTab.value = tab
    }
}
