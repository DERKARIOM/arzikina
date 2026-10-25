package com.naniger.arzikina.presentation.shop.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.domain.repository.SalesStatsRepository
import com.naniger.arzikina.domain.repository.ShopRepository
import com.naniger.arzikina.domain.shop.DailyRevenue
import com.naniger.arzikina.domain.shop.ProductRanking
import com.naniger.arzikina.domain.shop.ProductSalesStat
import com.naniger.arzikina.domain.shop.SalePeriod
import com.naniger.arzikina.domain.shop.SalesStatsRules
import com.naniger.arzikina.domain.shop.SalesSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** Critères de l'écran. Périodes offertes : jour, 7 jours, 30 jours (voir [STATS_PERIODS]). */
data class StatsCriteria(
    val period: SalePeriod = SalePeriod.LAST_7_DAYS,
    val ranking: ProductRanking = ProductRanking.BEST_SELLING,
    val nowMillis: Long = System.currentTimeMillis()
)

sealed interface StatsUiState {
    data object Loading : StatsUiState
    data object Error : StatsUiState
    data class Loaded(
        val criteria: StatsCriteria,
        val currencyCode: String,
        val summary: SalesSummary,
        /** Série continue (jours sans vente à 0) du premier au dernier jour de la période. */
        val daily: List<DailyRevenue>,
        val products: List<ProductSalesStat>
    ) : StatsUiState
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ShopStatsViewModel @Inject constructor(
    private val stats: SalesStatsRepository,
    shops: ShopRepository
) : ViewModel() {

    private val _criteria = MutableStateFlow(StatsCriteria())
    val criteria: StateFlow<StatsCriteria> = _criteria

    val uiState: StateFlow<StatsUiState> = _criteria
        .flatMapLatest { c ->
            val zone = ZoneId.systemDefault()
            val (from, to) = c.period.bounds(c.nowMillis, zone)
            val offset = zone.rules.getOffset(Instant.ofEpochMilli(c.nowMillis)).totalSeconds * 1000L
            combine(
                shops.observeShop(),
                stats.observeSummary(from, to),
                stats.observeDaily(from, to, offset),
                stats.observeProducts(from, to, c.ranking, PRODUCTS_LIMIT)
            ) { shop, summary, daily, products ->
                val filled = SalesStatsRules.fillGaps(
                    SalesStatsRules.dayKey(from, offset), SalesStatsRules.dayKey(to - 1, offset), daily
                )
                StatsUiState.Loaded(c, shop?.currencyCode.orEmpty(), summary, filled, products) as StatsUiState
            }
        }
        .catch { emit(StatsUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState.Loading)

    fun onPeriodChange(period: SalePeriod) = _criteria.update { it.copy(period = period, nowMillis = System.currentTimeMillis()) }
    fun onRankingChange(ranking: ProductRanking) = _criteria.update { it.copy(ranking = ranking) }

    companion object {
        const val PRODUCTS_LIMIT = 10
        val STATS_PERIODS = listOf(SalePeriod.TODAY, SalePeriod.LAST_7_DAYS, SalePeriod.LAST_30_DAYS)
    }
}
