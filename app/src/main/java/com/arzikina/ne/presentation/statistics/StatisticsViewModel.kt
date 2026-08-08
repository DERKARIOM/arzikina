package com.arzikina.ne.presentation.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.CategoryRepository
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
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** Nombre de mois affichés dans le graphique d'évolution. */
private const val EVOLUTION_MONTHS_COUNT = 6

/** Répartition des dépenses du mois en cours pour une catégorie. */
data class CategoryBreakdownItem(
    val category: Category?,
    val amountMinor: Long,
    val percentage: Float
)

/** Un point du graphique d'évolution mensuelle. */
data class MonthlyEvolutionPoint(
    val yearMonth: YearMonth,
    val incomeMinor: Long,
    val expenseMinor: Long
)

data class StatisticsUiState(
    val currencyCode: String,
    val categoryBreakdown: List<CategoryBreakdownItem>,
    val monthlyEvolution: List<MonthlyEvolutionPoint>
)

/**
 * ViewModel de l'écran Statistiques : répartition des dépenses du mois par
 * catégorie (camembert) et évolution des revenus/dépenses sur
 * [EVOLUTION_MONTHS_COUNT] mois (barres groupées).
 *
 * Limite volontaire : seules les transactions dont le compte est dans la
 * devise principale de l'utilisateur ([UserPreferencesRepository]) sont
 * prises en compte. Contrairement au tableau de bord (qui regroupe les
 * montants par devise), un graphique ne peut pas comparer plusieurs devises
 * sur un même axe sans conversion de change.
 */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<AppResult<StatisticsUiState>> = combine(
        accountRepository.observeAccounts(),
        categoryRepository.observeCategories(),
        transactionRepository.observeTransactions(),
        userPreferencesRepository.observePreferences()
    ) { accounts, categories, transactions, preferences ->
        val categoriesById = categories.associateBy { it.id }
        val primaryCurrencyAccountIds = accounts
            .filter { it.currencyCode == preferences.currencyCode }
            .map { it.id }
            .toSet()
        val relevantTransactions = transactions.filter { it.accountId in primaryCurrencyAccountIds }

        StatisticsUiState(
            currencyCode = preferences.currencyCode,
            categoryBreakdown = computeCategoryBreakdown(relevantTransactions, categoriesById),
            monthlyEvolution = computeMonthlyEvolution(relevantTransactions)
        )
    }
        .map<StatisticsUiState, AppResult<StatisticsUiState>> { AppResult.Success(it) }
        .catch { throwable -> emit(AppResult.Error(throwable.message ?: "Erreur inconnue", throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )

    private fun computeCategoryBreakdown(
        transactions: List<Transaction>,
        categoriesById: Map<Long, Category>
    ): List<CategoryBreakdownItem> {
        val currentMonth = YearMonth.now()
        val monthlyExpenses = transactions.filter {
            it.type == TransactionType.EXPENSE && it.date.toYearMonth() == currentMonth
        }
        val total = monthlyExpenses.sumOf { it.amount }
        if (total <= 0L) return emptyList()

        // categoryId n'est `null` que pour un transfert (voir TransactionType.TRANSFER), déjà
        // exclu de monthlyExpenses par le filtre `type == EXPENSE` ci-dessus ; mapNotNull couvre
        // quand même ce cas pour rester correct si l'invariant venait à changer.
        return monthlyExpenses
            .mapNotNull { transaction -> transaction.categoryId?.let { it to transaction } }
            .groupBy({ (categoryId, _) -> categoryId }, { (_, transaction) -> transaction })
            .map { (categoryId, txs) ->
                val amount = txs.sumOf { it.amount }
                CategoryBreakdownItem(
                    category = categoriesById[categoryId],
                    amountMinor = amount,
                    percentage = amount.toFloat() / total.toFloat()
                )
            }
            .sortedByDescending { it.amountMinor }
    }

    private fun computeMonthlyEvolution(transactions: List<Transaction>): List<MonthlyEvolutionPoint> {
        val currentMonth = YearMonth.now()
        val months = (EVOLUTION_MONTHS_COUNT - 1 downTo 0).map { currentMonth.minusMonths(it.toLong()) }
        val transactionsByMonth = transactions.groupBy { it.date.toYearMonth() }

        return months.map { month ->
            val monthTransactions = transactionsByMonth[month].orEmpty()
            MonthlyEvolutionPoint(
                yearMonth = month,
                incomeMinor = monthTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                expenseMinor = monthTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            )
        }
    }

    private fun Long.toYearMonth(): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))
}
