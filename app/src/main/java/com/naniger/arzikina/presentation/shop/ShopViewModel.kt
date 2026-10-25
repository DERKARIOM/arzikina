package com.naniger.arzikina.presentation.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.domain.repository.ProductRepository
import com.naniger.arzikina.domain.repository.SalesStatsRepository
import com.naniger.arzikina.domain.repository.ShopRepository
import com.naniger.arzikina.domain.shop.ProductRanking
import com.naniger.arzikina.domain.shop.ProductSalesStat
import com.naniger.arzikina.domain.shop.ProductSummary
import com.naniger.arzikina.domain.shop.SalePeriod
import com.naniger.arzikina.domain.shop.SaleSummary
import com.naniger.arzikina.domain.shop.SalesSummary
import com.naniger.arzikina.domain.shop.ShopStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/** État du tableau de bord Boutique. [isLoading] vaut vrai tant que la boutique n'est pas prête. */
data class ShopDashboardState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val currencyCode: String = "",
    val stats: ShopStats? = null,
    val alerts: List<ProductSummary> = emptyList(),
    val sales: ShopSalesDashboard = ShopSalesDashboard()
)

/**
 * Indicateurs de ventes du tableau de bord : « aujourd'hui » est fixe, [periodSummary] suit [period].
 * Les chiffres viennent d'agrégats SQL ; le CA ne compte que les ventes validées (voir `SalesSummary`).
 */
data class ShopSalesDashboard(
    val period: SalePeriod = SalePeriod.LAST_7_DAYS,
    val today: SalesSummary = SalesSummary.EMPTY,
    val periodSummary: SalesSummary = SalesSummary.EMPTY,
    val recent: List<SaleSummary> = emptyList(),
    val topProducts: List<ProductSalesStat> = emptyList()
)

/**
 * Tableau de bord de l'onglet Boutique : stock (étape 3) et ventes (étape 7). Une erreur du bloc ventes
 * n'empêche jamais l'affichage du stock.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ShopViewModel @Inject constructor(
    private val shops: ShopRepository,
    products: ProductRepository,
    salesStats: SalesStatsRepository
) : ViewModel() {

    private val creationFailed = MutableStateFlow(false)
    private var ensureStarted = false

    private val period = MutableStateFlow(SalePeriod.LAST_7_DAYS)

    /** Instant de référence des périodes : rafraîchi à chaque retour sur l'écran (minuit passé, etc.). */
    private val now = MutableStateFlow(System.currentTimeMillis())

    private val salesDashboard = combine(period, now) { p, n -> p to n }
        .flatMapLatest { (p, n) ->
            val zone = ZoneId.systemDefault()
            val (todayFrom, todayTo) = SalePeriod.TODAY.bounds(n, zone)
            val (from, to) = p.bounds(n, zone)
            combine(
                salesStats.observeSummary(todayFrom, todayTo),
                salesStats.observeSummary(from, to),
                salesStats.observeRecentSales(RECENT_SALES_LIMIT),
                salesStats.observeProducts(from, to, ProductRanking.BEST_SELLING, TOP_PRODUCTS_LIMIT)
            ) { today, summary, recent, top -> ShopSalesDashboard(p, today, summary, recent, top) }
        }
        .catch { emit(ShopSalesDashboard(period.value)) }

    val uiState: StateFlow<ShopDashboardState> = combine(
        shops.observeShop(),
        products.observeStats(),
        products.observeStockAlerts(ALERT_PREVIEW_LIMIT),
        creationFailed,
        salesDashboard
    ) { shop, stats, alerts, failed, sales ->
        ShopDashboardState(
            isLoading = shop == null && !failed,
            hasError = shop == null && failed,
            currencyCode = shop?.currencyCode.orEmpty(),
            stats = stats,
            alerts = alerts,
            sales = sales
        )
    }
        .catch { emit(ShopDashboardState(isLoading = false, hasError = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShopDashboardState())

    fun onPeriodSelected(selected: SalePeriod) = period.update { selected }

    /** À appeler quand l'écran redevient visible : recalcule « aujourd'hui » et les périodes. */
    fun refreshClock() = now.update { System.currentTimeMillis() }

    /** Crée la boutique à la première ouverture de l'onglet (idempotent côté repository). */
    fun ensureShop(defaultName: String) {
        if (ensureStarted) return
        ensureStarted = true
        viewModelScope.launch {
            try {
                shops.getOrCreateShop(defaultName)
                creationFailed.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureStarted = false
                creationFailed.value = true
            }
        }
    }

    companion object {
        /** Nombre maximal d'alertes affichées sur le tableau de bord (la liste complète est dans « Produits »). */
        const val ALERT_PREVIEW_LIMIT = 5
        const val RECENT_SALES_LIMIT = 3
        const val TOP_PRODUCTS_LIMIT = 3
    }
}
