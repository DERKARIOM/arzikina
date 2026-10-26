package com.naniger.arzikina.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Charges utiles de synchronisation du module Boutique (voir `server/api/config/entity_sync_configs.php`
 * et `claude/shop/SYNC-BOUTIQUE-CONTRAT.md`).
 *
 * Règles communes :
 * - les clés étrangères voyagent sous forme de `syncId` (`*SyncId`), jamais d'id Room (propre à chaque appareil) ;
 * - `stockMilli` (cache local recalculé depuis les mouvements) et `imageFileName` (photos : étape ultérieure)
 *   n'y figurent PAS volontairement ;
 * - côté SORTANT un booléen reste `Boolean`, côté ENTRANT le serveur renvoie `0`/`1` : d'où `Int` dans les
 *   `*ServerStateDto` (voir la KDoc de `AccountSyncPayload`).
 */

@Serializable
data class ShopSyncPayload(
    val id: String,
    val baseVersion: Int?,
    val name: String,
    val accountSyncId: String?,
    val currencyCode: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ShopServerStateDto(
    val id: String,
    val name: String,
    val accountSyncId: String? = null,
    val currencyCode: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)

@Serializable
data class ProductCategorySyncPayload(
    val id: String,
    val baseVersion: Int?,
    val shopSyncId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ProductCategoryServerStateDto(
    val id: String,
    val shopSyncId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)

@Serializable
data class ProductSyncPayload(
    val id: String,
    val baseVersion: Int?,
    val shopSyncId: String,
    val categorySyncId: String?,
    val name: String,
    val description: String?,
    val sku: String?,
    val barcode: String?,
    val unit: String,
    val purchasePriceMinor: Long?,
    val salePriceMinor: Long,
    val minStockMilli: Long,
    val expiryDate: Long?,
    /** Nom de la photo (UUID + `.jpg`), `null` explicite pour la retirer. L'image passe par `ProductImageApi`. */
    val imageFileName: String?,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ProductServerStateDto(
    val id: String,
    val shopSyncId: String,
    val categorySyncId: String? = null,
    val name: String,
    val description: String? = null,
    val sku: String? = null,
    val barcode: String? = null,
    val unit: String,
    val purchasePriceMinor: Long? = null,
    val salePriceMinor: Long,
    val minStockMilli: Long,
    val expiryDate: Long? = null,
    /** Absent tant que le serveur n'a pas la colonne (migration 014) ou qu'aucune photo n'a été envoyée. */
    val imageFileName: String? = null,
    /** `0`/`1`, PAS `Boolean`. */
    val isArchived: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)

@Serializable
data class StockMovementSyncPayload(
    val id: String,
    val baseVersion: Int?,
    val productSyncId: String,
    val type: String,
    val deltaMilli: Long,
    val unitCostMinor: Long?,
    val reason: String?,
    val reference: String?,
    val saleSyncId: String?,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class StockMovementServerStateDto(
    val id: String,
    val productSyncId: String,
    val type: String,
    val deltaMilli: Long,
    val unitCostMinor: Long? = null,
    val reason: String? = null,
    val reference: String? = null,
    val saleSyncId: String? = null,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)

@Serializable
data class SaleSyncPayload(
    val id: String,
    val baseVersion: Int?,
    val shopSyncId: String,
    val reference: String,
    val soldAt: Long,
    val currencyCode: String,
    val customerName: String?,
    val customerPhone: String?,
    val subtotalMinor: Long,
    val discountMinor: Long,
    val totalMinor: Long,
    val status: String,
    val cancelledAt: Long?,
    val cancelReason: String?,
    val issuedReceiptSyncId: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class SaleServerStateDto(
    val id: String,
    val shopSyncId: String,
    val reference: String,
    val soldAt: Long,
    val currencyCode: String,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val subtotalMinor: Long,
    val discountMinor: Long,
    val totalMinor: Long,
    val status: String,
    val cancelledAt: Long? = null,
    val cancelReason: String? = null,
    val issuedReceiptSyncId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)

@Serializable
data class SaleLineSyncPayload(
    val id: String,
    val baseVersion: Int?,
    val saleSyncId: String,
    val productSyncId: String,
    val productName: String,
    val unitPriceMinor: Long,
    val unitCostMinor: Long?,
    val quantityMilli: Long,
    val lineTotalMinor: Long,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class SaleLineServerStateDto(
    val id: String,
    val saleSyncId: String,
    val productSyncId: String,
    val productName: String,
    val unitPriceMinor: Long,
    val unitCostMinor: Long? = null,
    val quantityMilli: Long,
    val lineTotalMinor: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)

@Serializable
data class SalePaymentSyncPayload(
    val id: String,
    val baseVersion: Int?,
    val saleSyncId: String,
    val accountSyncId: String,
    val method: String,
    val amountMinor: Long,
    val paidAt: Long,
    val transactionSyncId: String?,
    val reversalTransactionSyncId: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class SalePaymentServerStateDto(
    val id: String,
    val saleSyncId: String,
    val accountSyncId: String,
    val method: String,
    val amountMinor: Long,
    val paidAt: Long,
    val transactionSyncId: String? = null,
    val reversalTransactionSyncId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Int
)
