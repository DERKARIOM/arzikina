package com.arzikina.ne.presentation.utilities.financialplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.repository.FinancialPlanRepository
import com.arzikina.ne.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel de l'écran liste "Planification" : une planification par carte, avec disponible/
 * prévu/reste et une barre de progression (voir cahier des charges, sections 3/4).
 *
 * `combine(observePlans(), observeAllItems())` puis [buildFinancialPlanUiItems] — même pattern que
 * [com.arzikina.ne.presentation.budget.BudgetViewModel] (voir sa doc pour le raisonnement : pas de
 * `combine(List<Flow>)` dynamique, un flux OBSERVANT TOUTES les dépenses prévues de l'utilisateur
 * une seule fois plutôt qu'un flux par planification). Le calcul lui-même (regroupement +
 * progression) est PARTAGÉ avec `AccountsViewModel` (onglet "Planification" de "Mes comptes") via
 * [buildFinancialPlanUiItems], pas dupliqué ici.
 */
@HiltViewModel
class FinancialPlansViewModel @Inject constructor(
    private val financialPlanRepository: FinancialPlanRepository
) : ViewModel() {

    val uiState: StateFlow<AppResult<List<FinancialPlanUiItem>>> = combine(
        financialPlanRepository.observePlans(),
        financialPlanRepository.observeAllItems()
    ) { plans, allItems ->
        buildFinancialPlanUiItems(plans, allItems)
    }
        .map<List<FinancialPlanUiItem>, AppResult<List<FinancialPlanUiItem>>> { AppResult.Success(it) }
        .catch { throwable -> emit(AppResult.Error(throwable.message ?: "Erreur inconnue", throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )
}
