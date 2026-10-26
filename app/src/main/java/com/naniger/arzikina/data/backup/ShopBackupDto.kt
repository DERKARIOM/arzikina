package com.naniger.arzikina.data.backup

import kotlinx.serialization.Serializable

/**
 * Données du module « Boutique » dans le fichier de sauvegarde. Chaque liste est vide par défaut : un
 * fichier exporté avant l'existence de la Boutique se restaure sans erreur (et la restauration vide alors
 * la boutique de l'utilisateur, comme pour toutes les autres tables remplacées — voir
 * `BackupRepositoryImpl.importBackup`).
 *
 * Comme les autres DTO : aucun `userId`, `syncId`, `version` ni `deletedAt` (techniques, régénérés à
 * l'import), et des `id` qui ne servent qu'à relier les lignes entre elles dans le fichier (ils sont tous
 * réattribués à l'import, voir [ShopBackupHandler]).
 *
 * Volontairement ABSENT : `sales.issuedReceiptId`. Les reçus émis ne font pas partie de la sauvegarde, leurs
 * identifiants n'ont donc aucun sens sur un autre appareil — conserver ce lien risquerait de pointer vers
 * un AUTRE reçu. Après restauration, une vente n'est plus liée à un reçu ; on peut le régénérer.
 */
@Serializable
data class ShopBackupDto(
    val shops: List<ShopDto> = emptyList(),
    val productCategories: List<ProductCategoryDto> = emptyList(),
    val products: List<ProductDto> = emptyList(),
    val stockMovements: List<StockMovementDto> = emptyList(),
    val sales: List<SaleDto> = emptyList(),
    val saleLines: List<SaleLineDto> = emptyList(),
    val salePayments: List<SalePaymentDto> = emptyList()
)

@Serializable
data class ShopDto(
    val id: Long,
    val name: String,
    val accountId: Long? = null,
    val currencyCode: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ProductCategoryDto(
    val id: Long,
    val shopId: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * [imageBase64] : la photo (JPEG ≤ 1024 px, quelques centaines de Ko au plus) est embarquée dans le fichier,
 * comme les PDF des reçus — `imageFileName` est un nom local, sans signification sur un autre appareil.
 */
@Serializable
data class ProductDto(
    val id: Long,
    val shopId: Long,
    val categoryId: Long? = null,
    val name: String,
    val description: String? = null,
    val sku: String? = null,
    val barcode: String? = null,
    val unit: String,
    val purchasePriceMinor: Long? = null,
    val salePriceMinor: Long,
    val minStockMilli: Long,
    val stockMilli: Long,
    val expiryDate: Long? = null,
    val imageBase64: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class StockMovementDto(
    val id: Long,
    val productId: Long,
    val type: String,
    val deltaMilli: Long,
    val unitCostMinor: Long? = null,
    val reason: String? = null,
    val reference: String? = null,
    val saleId: Long? = null,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class SaleDto(
    val id: Long,
    val shopId: Long,
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
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class SaleLineDto(
    val id: Long,
    val saleId: Long,
    val productId: Long,
    val productName: String,
    val unitPriceMinor: Long,
    val unitCostMinor: Long? = null,
    val quantityMilli: Long,
    val lineTotalMinor: Long,
    val createdAt: Long,
    val updatedAt: Long
)

/** [transactionId] / [reversalTransactionId] : réécrits vers les nouvelles transactions à l'import. */
@Serializable
data class SalePaymentDto(
    val id: Long,
    val saleId: Long,
    val accountId: Long,
    val method: String,
    val amountMinor: Long,
    val paidAt: Long,
    val transactionId: Long? = null,
    val reversalTransactionId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)
