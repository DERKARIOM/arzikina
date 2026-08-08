package com.arzikina.ne.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.CategoryRepository
import com.arzikina.ne.domain.repository.TransactionRepository
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.DatePeriods
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

/** Filtre par type, en plus du texte de recherche libre. */
enum class TransactionTypeFilter {
    ALL,
    INCOME,
    EXPENSE
}

/** Filtre par période, indépendant du filtre par type. */
enum class TransactionPeriodFilter {
    ALL,
    THIS_WEEK,
    THIS_MONTH
}

/**
 * Filtres appliqués à la liste des transactions. `accountId`/`categoryId`
 * valent `null` pour "tous" — voir [TransactionsScreen] où ce `null` est
 * représenté par une option "Tous les comptes"/"Toutes les catégories" dans
 * les menus déroulants.
 */
data class TransactionFilters(
    val query: String = "",
    val type: TransactionTypeFilter = TransactionTypeFilter.ALL,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val period: TransactionPeriodFilter = TransactionPeriodFilter.ALL
) {
    /** Exclut volontairement [query] : le champ de recherche a déjà son propre bouton "effacer". */
    val hasActiveFilters: Boolean
        get() = type != TransactionTypeFilter.ALL ||
            accountId != null ||
            categoryId != null ||
            period != TransactionPeriodFilter.ALL
}

/**
 * ViewModel de l'écran Transactions : liste combinée (transaction + compte +
 * catégorie), recherche instantanée et filtres, et suppression. L'ajout/
 * l'édition se fait via [TransactionFormViewModel].
 *
 * Le filtrage se fait en mémoire après combinaison des flux Room, comme le
 * reste de l'application (tableau de bord, statistiques, budgets) : pas de
 * requête SQL dynamique à maintenir pour chaque combinaison de filtres.
 */
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    private val _filters = MutableStateFlow(TransactionFilters())
    val filters: StateFlow<TransactionFilters> = _filters.asStateFlow()

    val accounts: StateFlow<List<Account>> = accountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<Category>> = categoryRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Groupée par jour (voir [groupByDay]) — même présentation que "Détail du
     * compte" ([com.arzikina.ne.presentation.accounts.AccountDetailViewModel]),
     * demande explicite de cohérence entre les deux écrans qui listent des
     * transactions au long cours (par opposition à l'aperçu "5 dernières" du
     * Dashboard, qui reste une liste plate).
     */
    val uiState: StateFlow<AppResult<List<TransactionDaySection>>> = combine(
        transactionRepository.observeTransactions(),
        accountRepository.observeAccounts(),
        categoryRepository.observeCategories(),
        _filters
    ) { transactions, accounts, categories, filters ->
        val accountsById = accounts.associateBy { it.id }
        val categoriesById = categories.associateBy { it.id }
        val today = LocalDate.now()
        val normalizedQuery = filters.query.trim()
        // Sur les transactions NON filtrées : voir computeRunningBalances.
        val runningBalances = computeRunningBalances(transactions, accounts)

        transactions
            .asSequence()
            .map { transaction ->
                TransactionUiItem(
                    transaction = transaction,
                    account = accountsById[transaction.accountId],
                    category = categoriesById[transaction.categoryId],
                    runningBalance = runningBalances[transaction.id]
                )
            }
            .filter { item -> matchesType(item.transaction.type, filters.type) }
            .filter { item -> filters.accountId == null || item.transaction.accountId == filters.accountId }
            .filter { item -> filters.categoryId == null || item.transaction.categoryId == filters.categoryId }
            .filter { item -> matchesPeriod(item.transaction.date, filters.period, today) }
            .filter { item -> matchesQuery(item, normalizedQuery) }
            .toList()
            .groupByDay()
    }
        .map<List<TransactionDaySection>, AppResult<List<TransactionDaySection>>> { AppResult.Success(it) }
        .catch { throwable -> emit(AppResult.Error(throwable.message ?: "Erreur inconnue", throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )

    fun onQueryChange(query: String) {
        _filters.update { it.copy(query = query) }
    }

    fun onTypeFilterChange(type: TransactionTypeFilter) {
        _filters.update { it.copy(type = type) }
    }

    fun onAccountFilterChange(accountId: Long?) {
        _filters.update { it.copy(accountId = accountId) }
    }

    fun onCategoryFilterChange(categoryId: Long?) {
        _filters.update { it.copy(categoryId = categoryId) }
    }

    fun onPeriodFilterChange(period: TransactionPeriodFilter) {
        _filters.update { it.copy(period = period) }
    }

    fun resetFilters() {
        _filters.update { it.copy(type = TransactionTypeFilter.ALL, accountId = null, categoryId = null, period = TransactionPeriodFilter.ALL) }
    }

    private fun matchesType(type: TransactionType, filter: TransactionTypeFilter): Boolean =
        when (filter) {
            TransactionTypeFilter.ALL -> true
            TransactionTypeFilter.INCOME -> type == TransactionType.INCOME
            TransactionTypeFilter.EXPENSE -> type == TransactionType.EXPENSE
        }

    private fun matchesPeriod(dateMillis: Long, filter: TransactionPeriodFilter, today: LocalDate): Boolean =
        when (filter) {
            TransactionPeriodFilter.ALL -> true
            TransactionPeriodFilter.THIS_WEEK -> DatePeriods.isInCurrentWeek(dateMillis, today)
            TransactionPeriodFilter.THIS_MONTH -> DatePeriods.isInCurrentMonth(dateMillis, today)
        }

    private fun matchesQuery(item: TransactionUiItem, query: String): Boolean {
        if (query.isEmpty()) return true
        return item.transaction.description.contains(query, ignoreCase = true) ||
            item.category?.name?.contains(query, ignoreCase = true) == true ||
            item.account?.name?.contains(query, ignoreCase = true) == true
    }
}
