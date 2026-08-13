package com.arzikina.ne.presentation.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.CardSecrets
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.AuthRepository
import com.arzikina.ne.domain.repository.CategoryRepository
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.TransactionRepository
import com.arzikina.ne.presentation.transactions.TransactionDaySection
import com.arzikina.ne.presentation.transactions.TransactionUiItem
import com.arzikina.ne.presentation.transactions.computeRunningBalances
import com.arzikina.ne.presentation.transactions.feeTransactionIds
import com.arzikina.ne.presentation.transactions.groupByDay
import com.arzikina.ne.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
 * État affiché par l'écran "Détail du compte". [sections] est trié du jour
 * le plus récent au plus ancien (voir [groupByDay]).
 *
 * @param cardHolderName voir [AccountUiItem.cardHolderName] (même
 * raisonnement : nom de l'utilisateur connecté, sans effet hors
 * [com.arzikina.ne.domain.model.AccountType.CREDIT_CARD]).
 */
data class AccountDetailUiState(
    val account: Account,
    val currentBalance: Long,
    val sections: List<TransactionDaySection>,
    val cardHolderName: String = ""
)

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    authRepository: AuthRepository,
    sessionManager: SessionManager
) : ViewModel() {

    val accountId: Long = savedStateHandle.get<Long>(ACCOUNT_ID_ARG) ?: 0L

    val uiState: StateFlow<AppResult<AccountDetailUiState>> = combine(
        accountRepository.observeAccounts(),
        transactionRepository.observeTransactions(),
        categoryRepository.observeCategories(),
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) flowOf(null) else authRepository.observeUser(userId)
        }
    ) { accounts, transactions, categories, user ->
        val account = accounts.find { it.id == accountId } ?: return@combine null
        val categoriesById = categories.associateBy { it.id }

        // Un transfert dont ce compte est la DESTINATION doit apparaître ici aussi (voir
        // TransactionType.TRANSFER) : accountId seul ne couvre que la jambe "source".
        // Inclut volontairement les transactions de frais de CE compte (voir feeTransactionIds
        // plus bas) : elles doivent compter dans le solde, seule leur LIGNE d'affichage est
        // filtrée ensuite.
        val accountTransactions = transactions.filter { it.accountId == accountId || it.transferAccountId == accountId }
        // `accountTransactions` suffit ici (pas besoin de la liste complète) : pour chaque
        // transfert filtré, computeCurrentBalances/computeRunningBalances savent déjà créditer
        // la jambe destination dès que transferAccountId == accountId (voir leur documentation).
        val currentBalance = computeCurrentBalances(listOf(account), accountTransactions)[account.id] ?: account.initialBalance
        val runningBalances = computeRunningBalances(accountTransactions, listOf(account))

        // Recherche du montant des frais liés sur TOUTE la liste (transactions, pas
        // accountTransactions) : le compte des frais d'une transaction de ce compte peut être un
        // AUTRE compte (voir feeTransactionIds).
        val transactionsById = transactions.associateBy { it.id }
        val feeTransactionIds = transactions.feeTransactionIds()
        val items = accountTransactions
            .filter { transaction -> transaction.id !in feeTransactionIds }
            .map { transaction ->
                val isTransferReceived = transaction.transferAccountId == accountId
                TransactionUiItem(
                    transaction = transaction,
                    account = account,
                    // categoryId est `null` pour un transfert (voir TransactionType.TRANSFER).
                    category = transaction.categoryId?.let { categoriesById[it] },
                    runningBalance = runningBalances[transaction.id to accountId],
                    isTransferReceived = isTransferReceived,
                    feeAmount = transaction.feeTransactionId?.let { transactionsById[it]?.amount }
                )
            }

        AccountDetailUiState(
            account = account,
            currentBalance = currentBalance,
            sections = items.groupByDay(),
            cardHolderName = user?.fullName.orEmpty()
        )
    }
        .map<AccountDetailUiState?, AppResult<AccountDetailUiState>> { state ->
            state?.let { AppResult.Success(it) } ?: AppResult.Error("Compte introuvable")
        }
        .catch { throwable -> emit(AppResult.Error(throwable.message ?: "Erreur inconnue", throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )

    fun deleteAccount() {
        viewModelScope.launch {
            accountRepository.deleteAccount(accountId)
        }
    }

    /**
     * Numéro complet + CVV DÉCHIFFRÉS, `null` par défaut (masqué). Vit ici plutôt que dans le
     * Fragment (contrairement au choix initial de `DashboardFragment.isBalanceHidden`) : la
     * révélation exige un déchiffrement via [AccountRepository.revealCardSecrets], une opération
     * de la couche data qui n'a rien à faire dans une Fragment — Clean Architecture oblige.
     */
    private val _cardSecrets = MutableStateFlow<CardSecrets?>(null)
    val cardSecrets: StateFlow<CardSecrets?> = _cardSecrets.asStateFlow()

    private var autoHideJob: Job? = null

    /** Bascule masqué <-> révélé. Ignoré si un compte classique appelle ceci par erreur : il n'y a
     * simplement jamais de secret enregistré pour lui, [revealCardSecrets] renverra `null`. */
    fun toggleCardSecrets() {
        if (_cardSecrets.value != null) {
            hideCardSecrets()
            return
        }
        autoHideJob?.cancel()
        viewModelScope.launch {
            _cardSecrets.value = accountRepository.revealCardSecrets(accountId)
            if (_cardSecrets.value != null) {
                autoHideJob = viewModelScope.launch {
                    delay(AUTO_HIDE_DELAY_MILLIS)
                    _cardSecrets.value = null
                }
            }
        }
    }

    /** Remasquage immédiat (bouton, [android.app.Activity.onPause] de l'écran, délai écoulé). */
    fun hideCardSecrets() {
        autoHideJob?.cancel()
        _cardSecrets.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // Le secret déchiffré ne doit jamais survivre au ViewModel (voir CardSecrets, "jamais
        // conservés au-delà de la session d'affichage courante").
        _cardSecrets.value = null
    }

    private companion object {
        const val ACCOUNT_ID_ARG = "accountId"

        /** Délai avant remasquage automatique des informations de la carte (section sécurité). */
        const val AUTO_HIDE_DELAY_MILLIS = 10_000L
    }
}
