package com.arzikina.ne.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Budget
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.BudgetRepository
import com.arzikina.ne.domain.repository.CategoryRepository
import com.arzikina.ne.domain.repository.TransactionRepository
import com.arzikina.ne.presentation.budget.BudgetUiItem
import com.arzikina.ne.presentation.transactions.TransactionUiItem
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.BudgetProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** Nombre de transactions récentes affichées sur le tableau de bord. */
private const val RECENT_TRANSACTIONS_LIMIT = 5

/**
 * État affiché par [DashboardScreen].
 *
 * [balances]/[monthlyIncome]/[monthlyExpense] sont des listes plutôt que des
 * [Long] uniques : voir [CurrencyAmount] pour le raisonnement (pas de
 * conversion de change, donc pas d'addition entre devises différentes).
 */
data class DashboardUiState(
    val balances: List<CurrencyAmount>,
    val monthlyIncome: List<CurrencyAmount>,
    val monthlyExpense: List<CurrencyAmount>,
    val recentTransactions: List<TransactionUiItem>,
    /**
     * Le budget le plus "urgent" (progression la plus élevée, dépassement
     * inclus) parmi tous les budgets actifs, ou `null` si aucun budget n'existe
     * encore. Un seul budget est mis en avant sur le tableau de bord ; la
     * liste complète reste dans l'onglet Budget (voir [BudgetUiItem]).
     */
    val featuredBudget: BudgetUiItem?
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    budgetRepository: BudgetRepository
) : ViewModel() {

    val uiState: StateFlow<AppResult<DashboardUiState>> = combine(
        accountRepository.observeAccounts(),
        transactionRepository.observeTransactions(),
        categoryRepository.observeCategories(),
        budgetRepository.observeBudgets()
    ) { accounts, transactions, categories, budgets ->
        val accountsById = accounts.associateBy { it.id }
        val categoriesById = categories.associateBy { it.id }

        val currentMonth = YearMonth.now()
        val monthlyTransactions = transactions.filter { transaction ->
            YearMonth.from(transaction.dateAsZonedDateTime()) == currentMonth
        }

        DashboardUiState(
            balances = computeBalances(accounts, transactions),
            monthlyIncome = sumByAccountCurrency(monthlyTransactions, TransactionType.INCOME, accountsById),
            monthlyExpense = sumByAccountCurrency(monthlyTransactions, TransactionType.EXPENSE, accountsById),
            recentTransactions = transactions.take(RECENT_TRANSACTIONS_LIMIT).map { transaction ->
                TransactionUiItem(
                    transaction = transaction,
                    account = accountsById[transaction.accountId],
                    category = categoriesById[transaction.categoryId]
                )
            },
            featuredBudget = featuredBudget(budgets, categoriesById, transactions, accountsById)
        )
    }
        .map<DashboardUiState, AppResult<DashboardUiState>> { AppResult.Success(it) }
        .catch { throwable -> emit(AppResult.Error(throwable.message ?: "Erreur inconnue", throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )

    /**
     * Solde de chaque compte = solde initial + somme signée de ses
     * transactions, regroupé par devise (voir [CurrencyAmount]).
     */
    private fun computeBalances(accounts: List<Account>, transactions: List<Transaction>): List<CurrencyAmount> {
        val signedByAccount = transactions
            .groupBy { it.accountId }
            .mapValues { (_, txs) -> txs.sumOf { it.signedAmount() } }

        return accounts
            .groupBy { it.currencyCode }
            .map { (currencyCode, accountsInCurrency) ->
                val total = accountsInCurrency.sumOf { account ->
                    account.initialBalance + (signedByAccount[account.id] ?: 0L)
                }
                CurrencyAmount(currencyCode, total)
            }
    }

    private fun sumByAccountCurrency(
        transactions: List<Transaction>,
        type: TransactionType,
        accountsById: Map<Long, Account>
    ): List<CurrencyAmount> =
        transactions
            .filter { it.type == type }
            .mapNotNull { transaction -> accountsById[transaction.accountId]?.let { it.currencyCode to transaction.amount } }
            .groupBy({ it.first }, { it.second })
            .map { (currencyCode, amounts) -> CurrencyAmount(currencyCode, amounts.sum()) }

    /**
     * Le budget le plus urgent = celui dont [BudgetProgress.Result.progress]
     * est le plus élevé (dépassement inclus) ; `null` s'il n'y a aucun budget.
     * Même règle de calcul que [com.arzikina.ne.presentation.budget.BudgetViewModel],
     * voir [BudgetProgress].
     */
    private fun featuredBudget(
        budgets: List<Budget>,
        categoriesById: Map<Long, Category>,
        transactions: List<Transaction>,
        accountsById: Map<Long, Account>
    ): BudgetUiItem? = budgets
        .map { budget ->
            val result = BudgetProgress.compute(budget, transactions, accountsById)
            BudgetUiItem(
                budget = budget,
                category = categoriesById[budget.categoryId],
                spentMinor = result.spentMinor,
                progress = result.progress
            )
        }
        .maxByOrNull { it.progress }

    private fun Transaction.signedAmount(): Long = if (type == TransactionType.INCOME) amount else -amount

    private fun Transaction.dateAsZonedDateTime() =
        Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault())
}
