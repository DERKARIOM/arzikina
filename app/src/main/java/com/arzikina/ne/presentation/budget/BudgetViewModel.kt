package com.arzikina.ne.presentation.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Budget
import com.arzikina.ne.domain.model.BudgetPeriod
import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.BudgetRepository
import com.arzikina.ne.domain.repository.CategoryRepository
import com.arzikina.ne.domain.repository.TransactionRepository
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.DatePeriods
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * ViewModel de l'écran Budget : liste des budgets avec leur progression sur
 * la période en cours. L'ajout/l'édition se fait via [BudgetFormViewModel].
 *
 * La progression n'est jamais stockée (voir [Budget]) : elle est recalculée
 * à chaque changement de transaction, comme les agrégats du tableau de bord.
 */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository
) : ViewModel() {

    val uiState: StateFlow<AppResult<List<BudgetUiItem>>> = combine(
        budgetRepository.observeBudgets(),
        categoryRepository.observeCategories(),
        accountRepository.observeAccounts(),
        transactionRepository.observeTransactions()
    ) { budgets, categories, accounts, transactions ->
        val categoriesById = categories.associateBy { it.id }
        val accountsById = accounts.associateBy { it.id }
        val today = LocalDate.now()

        budgets.map { budget ->
            val spent = spentOnCurrentPeriod(budget, transactions, accountsById, today)
            BudgetUiItem(
                budget = budget,
                category = categoriesById[budget.categoryId],
                spentMinor = spent,
                progress = if (budget.limitAmount > 0L) spent.toFloat() / budget.limitAmount.toFloat() else 0f
            )
        }
    }
        .map<List<BudgetUiItem>, AppResult<List<BudgetUiItem>>> { AppResult.Success(it) }
        .catch { throwable -> emit(AppResult.Error(throwable.message ?: "Erreur inconnue", throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )

    fun deleteBudget(id: Long) {
        viewModelScope.launch {
            budgetRepository.deleteBudget(id)
        }
    }

    private fun spentOnCurrentPeriod(
        budget: Budget,
        transactions: List<Transaction>,
        accountsById: Map<Long, Account>,
        today: LocalDate
    ): Long =
        transactions
            .asSequence()
            .filter { it.categoryId == budget.categoryId && it.type == TransactionType.EXPENSE }
            .filter { accountsById[it.accountId]?.currencyCode == budget.currencyCode }
            .filter { isInCurrentPeriod(it.date, budget.period, today) }
            .sumOf { it.amount }

    private fun isInCurrentPeriod(dateMillis: Long, period: BudgetPeriod, today: LocalDate): Boolean =
        when (period) {
            BudgetPeriod.MONTHLY -> DatePeriods.isInCurrentMonth(dateMillis, today)
            BudgetPeriod.WEEKLY -> DatePeriods.isInCurrentWeek(dateMillis, today)
        }
}
