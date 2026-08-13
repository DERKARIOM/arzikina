package com.arzikina.ne.presentation.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.BudgetRepository
import com.arzikina.ne.domain.repository.CategoryRepository
import com.arzikina.ne.domain.repository.TransactionRepository
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.BudgetProgress
import com.arzikina.ne.util.PersonalStatistics
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

    val uiState: StateFlow<AppResult<List<BudgetUiItem>>> = combine(
        budgetRepository.observeBudgets(),
        categoryRepository.observeCategories(),
        accountRepository.observeAccounts(),
        transactionRepository.observeTransactions()
    ) { budgets, categories, accounts, transactions ->
        val categoriesById = categories.associateBy { it.id }
        val accountsById = accounts.associateBy { it.id }
        val personalScope = PersonalStatistics.scope(accounts, transactions)

        budgets.map { budget ->
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

    fun deleteBudget(id: Long) {
        viewModelScope.launch {
            budgetRepository.deleteBudget(id)
        }
    }
}
