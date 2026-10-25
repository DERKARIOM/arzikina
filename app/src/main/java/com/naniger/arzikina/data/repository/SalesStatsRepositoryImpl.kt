package com.naniger.arzikina.data.repository

import com.naniger.arzikina.data.local.dao.SaleDao
import com.naniger.arzikina.data.local.dao.SaleLineDao
import com.naniger.arzikina.data.mapper.toDomain
import com.naniger.arzikina.domain.repository.SalesStatsRepository
import com.naniger.arzikina.domain.shop.DailyRevenue
import com.naniger.arzikina.domain.shop.ProductRanking
import com.naniger.arzikina.domain.shop.ProductSalesStat
import com.naniger.arzikina.domain.shop.SaleSummary
import com.naniger.arzikina.domain.shop.SalesSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Indicateurs de ventes : uniquement des agrégats SQL (aucune liste de ventes chargée en mémoire),
 * limités à la boutique active. Les flux se ré-émettent quand les ventes changent (Room observe les tables).
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class SalesStatsRepositoryImpl @Inject constructor(
    private val saleDao: SaleDao,
    private val saleLineDao: SaleLineDao,
    private val activeShop: ActiveShopProvider
) : SalesStatsRepository {

    override fun observeSummary(fromMillis: Long, toMillis: Long): Flow<SalesSummary> =
        activeShop.observeScope().flatMapLatest { scope ->
            if (scope == null) flowOf(SalesSummary.EMPTY)
            else saleDao.observeSummary(scope.userId, scope.shopId, fromMillis, toMillis).map {
                SalesSummary(it.salesCount, it.revenueMinor, it.collectedMinor, it.cancelledCount, it.cancelledMinor)
            }
        }.distinctUntilChanged()

    override fun observeDaily(fromMillis: Long, toMillis: Long, offsetMillis: Long): Flow<List<DailyRevenue>> =
        activeShop.observeScope().flatMapLatest { scope ->
            if (scope == null) flowOf(emptyList())
            else saleDao.observeDaily(scope.userId, scope.shopId, fromMillis, toMillis, offsetMillis).map { rows ->
                rows.map { DailyRevenue(it.dayKey, it.revenueMinor, it.salesCount) }
            }
        }

    override fun observeProducts(fromMillis: Long, toMillis: Long, ranking: ProductRanking, limit: Int): Flow<List<ProductSalesStat>> =
        activeShop.observeScope().flatMapLatest { scope ->
            if (scope == null) flowOf(emptyList())
            else saleLineDao.observeProductStats(scope.userId, scope.shopId, fromMillis, toMillis, ranking.ordinal, limit).map { rows ->
                rows.map { ProductSalesStat(it.productId, it.name, it.quantityMilli, it.revenueMinor, it.grossMarginMinor) }
            }
        }

    override fun observeRecentSales(limit: Int): Flow<List<SaleSummary>> =
        activeShop.observeScope().flatMapLatest { scope ->
            if (scope == null) flowOf(emptyList())
            else saleDao.observeSummaries(
                userId = scope.userId, shopId = scope.shopId, pattern = "%", status = null,
                fromMillis = Long.MIN_VALUE, toMillis = Long.MAX_VALUE, sort = 0, limit = limit
            ).map { rows -> rows.map { it.toDomain() } }
        }
}
