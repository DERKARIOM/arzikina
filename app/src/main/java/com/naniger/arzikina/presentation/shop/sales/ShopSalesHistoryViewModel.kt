package com.naniger.arzikina.presentation.shop.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.repository.AccountRepository
import com.naniger.arzikina.domain.repository.SaleRepository
import com.naniger.arzikina.domain.shop.SaleFilter
import com.naniger.arzikina.domain.shop.SalePeriod
import com.naniger.arzikina.domain.shop.SaleSort
import com.naniger.arzikina.domain.shop.SaleStatus
import com.naniger.arzikina.domain.shop.SaleSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.ZoneId
import javax.inject.Inject

/** Critères choisis à l'écran (la période est une valeur prédéfinie, ses bornes sont calculées à la demande). */
data class SalesHistoryCriteria(
    val query: String = "",
    val status: SaleStatus? = null,
    val period: SalePeriod = SalePeriod.ALL,
    val sort: SaleSort = SaleSort.DATE
) {
    val isActive: Boolean get() = query.isNotBlank() || status != null || period != SalePeriod.ALL
}

sealed interface SalesHistoryUiState {
    data object Loading : SalesHistoryUiState
    data object Error : SalesHistoryUiState
    data class Loaded(
        val items: List<SaleSummary>,
        val accountsById: Map<Long, Account>,
        val filtersActive: Boolean
    ) : SalesHistoryUiState
}

/** Historique des ventes : filtrage et tri en SQL, les comptes ne servent qu'à afficher le nom crédité. */
@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ShopSalesHistoryViewModel @Inject constructor(
    private val sales: SaleRepository,
    accounts: AccountRepository
) : ViewModel() {

    private val _criteria = MutableStateFlow(SalesHistoryCriteria())
    val criteria: StateFlow<SalesHistoryCriteria> = _criteria.asStateFlow()

    val uiState: StateFlow<SalesHistoryUiState> = _criteria
        .debounce { if (it.query.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .flatMapLatest { c ->
            combine(sales.observeSales(c.toFilter()), accounts.observeAccounts()) { items, all ->
                SalesHistoryUiState.Loaded(items, all.associateBy { it.id }, c.isActive) as SalesHistoryUiState
            }.catch { emit(SalesHistoryUiState.Error) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SalesHistoryUiState.Loading)

    fun onQueryChange(query: String) = _criteria.update { it.copy(query = query) }
    fun onStatusChange(status: SaleStatus?) = _criteria.update { it.copy(status = status) }
    fun onPeriodChange(period: SalePeriod) = _criteria.update { it.copy(period = period) }
    fun onSortChange(sort: SaleSort) = _criteria.update { it.copy(sort = sort) }

    private fun SalesHistoryCriteria.toFilter(): SaleFilter {
        val (from, to) = period.bounds(System.currentTimeMillis(), ZoneId.systemDefault())
        return SaleFilter(query = query, status = status, fromMillis = from, toMillis = to, sort = sort)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
