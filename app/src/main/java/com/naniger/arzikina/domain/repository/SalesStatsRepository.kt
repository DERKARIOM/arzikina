package com.naniger.arzikina.domain.repository

import com.naniger.arzikina.domain.shop.DailyRevenue
import com.naniger.arzikina.domain.shop.ProductRanking
import com.naniger.arzikina.domain.shop.ProductSalesStat
import com.naniger.arzikina.domain.shop.SaleSummary
import com.naniger.arzikina.domain.shop.SalesSummary
import kotlinx.coroutines.flow.Flow

/** Lecture seule des indicateurs de ventes de la boutique active (périodes `[from, to)` en millisecondes). */
interface SalesStatsRepository {
    fun observeSummary(fromMillis: Long, toMillis: Long): Flow<SalesSummary>

    /** CA par jour calendaire ; [offsetMillis] = décalage du fuseau de l'utilisateur. Jours sans vente absents. */
    fun observeDaily(fromMillis: Long, toMillis: Long, offsetMillis: Long): Flow<List<DailyRevenue>>

    fun observeProducts(fromMillis: Long, toMillis: Long, ranking: ProductRanking, limit: Int): Flow<List<ProductSalesStat>>

    /** Dernières ventes (validées ou annulées), plus récentes d'abord. */
    fun observeRecentSales(limit: Int): Flow<List<SaleSummary>>
}
