package com.arzikina.ne.presentation.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.CategoryRepository
import com.arzikina.ne.domain.repository.TransactionRepository
import com.arzikina.ne.presentation.transactions.TransactionDaySection
import com.arzikina.ne.presentation.transactions.TransactionUiItem
import com.arzikina.ne.presentation.transactions.computeRunningBalances
import com.arzikina.ne.presentation.transactions.groupByDay
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
 * État affiché par l'écran "Détail du compte". [sections] est trié du jour
 * le plus récent au plus ancien (voir [groupByDay]).
 */
data class AccountDetailUiState(
    val account: Account,
    val currentBalance: Long,
    val sections: List<TransactionDaySection>
)

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    val accountId: Long = savedStateHandle.get<Long>(ACCOUNT_ID_ARG) ?: 0L

    val uiState: StateFlow<AppResult<AccountDetailUiState>> = combine(
        accountRepository.observeAccounts(),
        transactionRepository.observeTransactions(),
        categoryRepository.observeCategories()
    ) { accounts, transactions, categories ->
        val account = accounts.find { it.id == accountId } ?: return@combine null
        val categoriesById = categories.associateBy { it.id }

        val accountTransactions = transactions.filter { it.accountId == accountId }
        val currentBalance = computeCurrentBalances(listOf(account), accountTransactions)[account.id] ?: account.initialBalance
        val runningBalances = computeRunningBalances(accountTransactions, listOf(account))

        val items = accountTransactions.map { transaction ->
            TransactionUiItem(
                transaction = transaction,
                account = account,
                // categoryId est `null` pour un transfert (voir TransactionType.TRANSFER).
                category = transaction.categoryId?.let { categoriesById[it] },
                runningBalance = runningBalances[transaction.id]
            )
        }

        AccountDetailUiState(account = account, currentBalance = currentBalance, sections = items.groupByDay())
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

    private companion object {
        const val ACCOUNT_ID_ARG = "accountId"
    }
}
