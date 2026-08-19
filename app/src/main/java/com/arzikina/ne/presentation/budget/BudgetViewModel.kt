package com.arzikina.ne.presentation.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Budget
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.BudgetRepository
import com.arzikina.ne.domain.repository.CategoryRepository
import com.arzikina.ne.domain.repository.TransactionRepository
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.BudgetPeriodStatus
import com.arzikina.ne.util.BudgetProgress
import com.arzikina.ne.util.PersonalStatistics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Filtre optionnel par statut (voir cahier des charges "Amélioration de la fonctionnalité Budget —
 * Gestion d'une période", section filtres) — ne s'applique qu'aux budgets à période fixe (voir
 * [BudgetPeriodStatus]) : un budget récurrent legacy ([BudgetPeriodStatus.of] retourne `null`,
 * aucun statut fixe) ne correspond jamais à [UPCOMING]/[ONGOING]/[COMPLETED], seulement à [ALL].
 */
enum class BudgetStatusFilterOption {
    ALL,
    UPCOMING,
    ONGOING,
    COMPLETED
}

/**
 * ViewModel de l'écran Budget : liste des budgets avec leur progression sur
 * la période en cours. L'ajout/l'édition se fait via [BudgetFormViewModel].
 *
 * La progression n'est jamais stockée en base : elle est recalculée à chaque
 * changement de transaction via [BudgetProgress], partagé avec l'aperçu
 * Budget du tableau de bord (voir [com.arzikina.ne.presentation.dashboard.DashboardViewModel]).
 *
 * Un [com.arzikina.ne.domain.model.Budget] n'a aujourd'hui aucun lien vers un compte précis
 * (pas de champ `accountId` — uniquement `categoryId` + période) : TOUS les budgets excluent donc
 * par défaut les transactions des comptes exclus des statistiques ([PersonalStatistics]), sans
 * exception possible pour l'instant. Un futur rattachement explicite budget↔compte nécessiterait
 * sa propre évolution du modèle de données.
 */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository
) : ViewModel() {

    private val _statusFilter = MutableStateFlow(BudgetStatusFilterOption.ALL)
    val statusFilter: StateFlow<BudgetStatusFilterOption> = _statusFilter.asStateFlow()

    val uiState: StateFlow<AppResult<List<BudgetUiItem>>> = combine(
        budgetRepository.observeBudgets(),
        categoryRepository.observeCategories(),
        accountRepository.observeAccounts(),
        transactionRepository.observeTransactions(),
        _statusFilter
    ) { budgets, categories, accounts, transactions, statusFilter ->
        val categoriesById = categories.associateBy { it.id }
        val accountsById = accounts.associateBy { it.id }
        val personalScope = PersonalStatistics.scope(accounts, transactions)

        budgets
            .filter { budget -> matchesStatusFilter(budget, statusFilter) }
            .map { budget ->
                val result = BudgetProgress.compute(budget, personalScope.transactions, accountsById)
                BudgetUiItem(
                    budget = budget,
                    category = categoriesById[budget.categoryId],
                    spentMinor = result.spentMinor,
                    progress = result.progress
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

    fun onStatusFilterChange(filter: BudgetStatusFilterOption) {
        _statusFilter.value = filter
    }

    fun deleteBudget(id: Long) {
        viewModelScope.launch {
            budgetRepository.deleteBudget(id)
        }
    }

    /** Voir [BudgetStatusFilterOption], doc de tête : un budget récurrent legacy ([status] `null`)
     * ne correspond jamais à un filtre autre que [BudgetStatusFilterOption.ALL]. */
    private fun matchesStatusFilter(budget: Budget, filter: BudgetStatusFilterOption): Boolean {
        if (filter == BudgetStatusFilterOption.ALL) return true
        val status = BudgetPeriodStatus.of(budget.startDate, budget.endDate) ?: return false
        return when (filter) {
            BudgetStatusFilterOption.ALL -> true
            BudgetStatusFilterOption.UPCOMING -> status == BudgetPeriodStatus.UPCOMING
            BudgetStatusFilterOption.ONGOING -> status == BudgetPeriodStatus.ONGOING
            BudgetStatusFilterOption.COMPLETED -> status == BudgetPeriodStatus.COMPLETED
        }
    }
}
