package com.naniger.arzikina.presentation.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.domain.model.AccountType
import com.naniger.arzikina.domain.repository.AccountRepository
import com.naniger.arzikina.domain.repository.AuthRepository
import com.naniger.arzikina.domain.repository.SessionManager
import com.naniger.arzikina.domain.repository.TransactionRepository
import com.naniger.arzikina.util.AppResult
import com.naniger.arzikina.util.technicalMessage
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
import kotlinx.coroutines.launch
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
 * `fragment_accounts.xml`) — aucun onglet ne désigne une nouvelle entité, ce sont de simples
 * filtres d'affichage sur la même liste de comptes (voir [matchesTab]) :
 * - [BANK_CARDS] : [AccountType.CREDIT_CARD], le seul type rendu comme une carte bancaire visuelle
 *   (voir `AccountsAdapter`/`item_account_credit_card.xml`) ;
 * - [SAVINGS_GOALS] (« Épargne ») : [AccountType.SAVINGS_GOAL] — les objectifs d'épargne sont des
 *   comptes à part entière (même carte, progression en plus, voir `AccountCardBinder`) ;
 * - [ACCOUNTS] : tous les autres types (`CASH`, `BANK`, `MOBILE_MONEY`, `SAVINGS`).
 *
 * Les planifications financières ne sont plus affichées ici (ancien 3e onglet « Planification ») :
 * elles restent accessibles par leur écran dédié (Utilitaires → Planification).
 *
 * ORDRE DE DÉCLARATION SIGNIFICATIF : `AccountsFragment.animateTabSwitch` utilise directement
 * `.ordinal` (0/1/2, dans cet ordre) comme position pour choisir le sens de l'animation de
 * transition — ne pas réordonner ces 3 valeurs sans mettre à jour cette logique en conséquence.
 */
enum class AccountsDisplayTab { ACCOUNTS, BANK_CARDS, SAVINGS_GOALS }

/** Voir [AccountsDisplayTab] : chaque compte correspond à EXACTEMENT un onglet (les 3 filtres
 * sont mutuellement exclusifs et couvrent tous les types) — aucun compte n'apparaît deux fois,
 * aucun n'est perdu. */
fun AccountUiItem.matchesTab(tab: AccountsDisplayTab): Boolean = when (tab) {
    AccountsDisplayTab.BANK_CARDS -> account.type == AccountType.CREDIT_CARD
    AccountsDisplayTab.SAVINGS_GOALS -> account.type == AccountType.SAVINGS_GOAL
    AccountsDisplayTab.ACCOUNTS ->
        account.type != AccountType.CREDIT_CARD && account.type != AccountType.SAVINGS_GOAL
}

/**
 * État et actions de l'écran "Liste des comptes".
 */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
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
        .catch { throwable -> emit(AppResult.Error(throwable.technicalMessage(), throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )

    /** Voir [AccountsDisplayTab] : appelé par `AccountsFragment` au clic sur `btnAccounts`/
     * `btnBankCards`/`btnSavingsGoals`. */
    fun onTabSelected(tab: AccountsDisplayTab) {
        _selectedTab.value = tab
    }

    /** Persistance d'un réordonnancement par glisser-déposer (voir `AccountsFragment`, chaque
     * onglet réordonnant uniquement sa propre sous-liste) — délègue entièrement à
     * [AccountRepository.reorderAccounts] (transaction Room + enfilage sync + déclenchement
     * immédiat, voir sa KDoc) : ce ViewModel n'ajoute aucune logique propre. [uiState] reflète
     * automatiquement le nouvel ordre à la prochaine émission de `observeAccounts()` (Flow Room),
     * aucune mise à jour manuelle de l'état ici. */
    fun reorderAccounts(orderedIds: List<Long>) {
        viewModelScope.launch { accountRepository.reorderAccounts(orderedIds) }
    }
}
