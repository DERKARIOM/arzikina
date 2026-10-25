package com.naniger.arzikina.domain.shop

/**
 * Indicateurs de ventes d'une période `[from, to)`.
 *
 * Règles (une seule source de vérité) :
 * - le chiffre d'affaires ne compte que les ventes `CONFIRMED` (une vente annulée en sort, ses
 *   remboursements sont isolés dans [cancelledCount] / [cancelledMinor]) ;
 * - ce n'est PAS un bénéfice : la marge brute est exposée à part, par produit, et seulement si le prix
 *   d'achat est connu ;
 * - [collectedMinor] = somme des encaissements des ventes validées ; [remainingDueMinor] en découle
 *   (toujours 0 en V1 : paiement intégral à la vente, les crédits arrivent en V2).
 */
data class SalesSummary(
    val salesCount: Int,
    val revenueMinor: Long,
    val collectedMinor: Long,
    val cancelledCount: Int,
    val cancelledMinor: Long
) {
    /** Panier moyen (arrondi à l'unité mineure la plus proche) ; `null` sans vente : jamais un 0 trompeur. */
    val averageBasketMinor: Long?
        get() = if (salesCount == 0) null else (revenueMinor + salesCount / 2) / salesCount

    val remainingDueMinor: Long get() = (revenueMinor - collectedMinor).coerceAtLeast(0L)

    companion object {
        val EMPTY = SalesSummary(0, 0L, 0L, 0, 0L)
    }
}

/** Chiffre d'affaires d'un jour calendaire (clé = numéro de jour local, voir [SalesStatsRules]). */
data class DailyRevenue(val dayKey: Long, val revenueMinor: Long, val salesCount: Int)

/**
 * Performance d'un produit sur la période (ventes validées uniquement).
 *
 * @param grossMarginMinor marge brute AVANT remise (ventes − coût d'achat des lignes dont le coût est
 * connu) ; `null` si aucune ligne n'a de coût. Ce n'est pas un bénéfice net.
 */
data class ProductSalesStat(
    val productId: Long,
    val name: String,
    val quantityMilli: Long,
    val revenueMinor: Long,
    val grossMarginMinor: Long?
)

enum class ProductRanking { BEST_SELLING, LEAST_SOLD, MOST_PROFITABLE }

/** Fonctions pures de la période (testables sans Android). */
object SalesStatsRules {
    const val DAY_MILLIS = 86_400_000L

    /** Numéro de jour local d'un instant, pour regrouper en jours calendaires avec le décalage [offsetMillis]. */
    fun dayKey(millis: Long, offsetMillis: Long): Long = Math.floorDiv(millis + offsetMillis, DAY_MILLIS)

    /**
     * Série continue de jours entre [fromKey] et [toKey] inclus : les jours sans vente valent 0
     * (une courbe ne doit pas « sauter » les jours creux).
     */
    fun fillGaps(fromKey: Long, toKey: Long, data: List<DailyRevenue>): List<DailyRevenue> {
        if (toKey < fromKey) return emptyList()
        val byDay = data.associateBy { it.dayKey }
        return (fromKey..toKey).map { byDay[it] ?: DailyRevenue(it, 0L, 0) }
    }
}
