package com.naniger.arzikina.domain.shop

import com.naniger.arzikina.domain.model.PaymentMethod
import com.naniger.arzikina.domain.model.ReceiptDiscount

/** Une ligne du panier envoyée à la validation : seul le produit et la quantité (milli-unités) comptent. */
data class SaleLineRequest(
    val productId: Long,
    val quantityMilli: Long
)

/**
 * Demande de validation d'une vente.
 *
 * Le prix et le coût ne figurent PAS ici : ils sont lus en base DANS la transaction de validation,
 * pour que la vente enregistre toujours le prix réellement en vigueur (jamais un prix périmé
 * affiché à l'écran).
 *
 * [draftKey] : clé d'idempotence générée à l'ouverture du panier (UUID). Revalider le même panier
 * (double clic, nouvelle tentative) retrouve la vente déjà enregistrée au lieu d'en créer une autre.
 */
data class SaleRequest(
    val draftKey: String,
    val lines: List<SaleLineRequest>,
    val accountId: Long,
    val discount: ReceiptDiscount = ReceiptDiscount.None,
    val customerName: String? = null,
    val customerPhone: String? = null
)

/** Ligne d'une vente enregistrée. Prix et coût sont ceux APPLIQUÉS au moment de la vente. */
data class SaleLine(
    val id: Long,
    val productId: Long,
    val productName: String,
    val unitPriceMinor: Long,
    val unitCostMinor: Long?,
    val quantityMilli: Long,
    val lineTotalMinor: Long
)

/** Vente enregistrée, avec ses lignes et son encaissement. */
data class Sale(
    val id: Long,
    val reference: String,
    val soldAt: Long,
    val currencyCode: String,
    val customerName: String?,
    val customerPhone: String?,
    val subtotalMinor: Long,
    val discountMinor: Long,
    val totalMinor: Long,
    val status: SaleStatus,
    val accountId: Long?,
    val paymentMethod: PaymentMethod?,
    val cancelledAt: Long? = null,
    val cancelReason: String? = null,
    /** Reçu émis pour cette vente (module « Reçus émis »), `null` tant qu'il n'a pas été généré. */
    val issuedReceiptId: Long? = null,
    val lines: List<SaleLine>
)

/** Ligne de l'historique des ventes (pas de lignes de produits : elles sont chargées dans le détail). */
data class SaleSummary(
    val id: Long,
    val reference: String,
    val soldAt: Long,
    val totalMinor: Long,
    val currencyCode: String,
    val status: SaleStatus,
    val customerName: String?,
    val accountId: Long?,
    val paymentMethod: PaymentMethod?,
    val hasReceipt: Boolean
)

/** Tri de l'historique : les plus récentes d'abord, ou les plus grosses ventes d'abord. */
enum class SaleSort { DATE, AMOUNT }

/**
 * Filtres de l'historique. [fromMillis] inclus, [toMillis] exclu (bornes d'une période, voir [SalePeriod]) ;
 * [status] `null` = toutes les ventes.
 */
data class SaleFilter(
    val query: String = "",
    val status: SaleStatus? = null,
    val fromMillis: Long = Long.MIN_VALUE,
    val toMillis: Long = Long.MAX_VALUE,
    val sort: SaleSort = SaleSort.DATE
)

/** Instantané d'un produit au moment de la validation (lu dans la transaction). */
data class SaleProductSnapshot(
    val id: Long,
    val name: String,
    val unit: ProductUnit,
    val salePriceMinor: Long,
    val purchasePriceMinor: Long?,
    val stockMilli: Long,
    val isArchived: Boolean
)

/** Ligne planifiée : prix/coût figés et montant calculé. */
data class PlannedSaleLine(
    val productId: Long,
    val productName: String,
    val unitPriceMinor: Long,
    val unitCostMinor: Long?,
    val quantityMilli: Long,
    val lineTotalMinor: Long,
    val newStockMilli: Long
)

/** Résultat PUR du calcul d'une vente : tout ce que le repository doit écrire. */
data class SalePlan(
    val lines: List<PlannedSaleLine>,
    val subtotalMinor: Long,
    val discountMinor: Long,
    val totalMinor: Long,
    val customerName: String?,
    val customerPhone: String?
)
