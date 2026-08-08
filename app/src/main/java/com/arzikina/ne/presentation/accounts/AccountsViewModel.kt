package com.arzikina.ne.presentation.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.TransactionRepository
import com.arzikina.ne.domain.repository.UserPreferencesRepository
import com.arzikina.ne.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * État affiché par l'écran "Mes comptes".
 *
 * [totalBalance] est réduit à une seule devise — celle définie comme
 * "principale" dans les Paramètres ([UserPreferencesRepository]) — plutôt
 * que d'afficher une ligne par devise comme le fait le Dashboard : demande
 * explicite d'une carte à une seule ligne. Les comptes tenus dans une AUTRE
 * devise sont exclus de ce total (additionner des devises différentes sans
 * taux de change donnerait un nombre erroné) ; même règle déjà appliquée par
 * [com.arzikina.ne.presentation.statistics.StatisticsViewModel]. Leur solde
 * individuel reste bien sûr visible sur leur propre ligne dans [accounts].
 */
data class AccountsUiState(
    val accounts: List<AccountUiItem>,
    val totalBalance: CurrencyAmount
)

/**
 * État et actions de l'écran "Liste des comptes".
 */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<AppResult<AccountsUiState>> = combine(
        accountRepository.observeAccounts(),
        transactionRepository.observeTransactions(),
        userPreferencesRepository.observePreferences()
    ) { accounts, transactions, preferences ->
        val signedByAccount = transactions
            .groupBy { it.accountId }
            .mapValues { (_, txs) -> txs.sumOf { it.signedAmount() } }

        val items = accounts.map { account ->
            AccountUiItem(
                account = account,
                currentBalance = account.initialBalance + (signedByAccount[account.id] ?: 0L)
            )
        }

        val totalBalanceMinor = items
            .filter { it.account.currencyCode == preferences.currencyCode }
            .sumOf { it.currentBalance }

        AccountsUiState(
            accounts = items,
            totalBalance = CurrencyAmount(preferences.currencyCode, totalBalanceMinor)
        )
    }
        .map<AccountsUiState, AppResult<AccountsUiState>> { AppResult.Success(it) }
        .catch { throwable -> emit(AppResult.Error(throwable.message ?: "Erreur inconnue", throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )

    fun deleteAccount(id: Long) {
        viewModelScope.launch {
            accountRepository.deleteAccount(id)
        }
    }

    private fun Transaction.signedAmount(): Long = if (type == TransactionType.INCOME) amount else -amount
}
