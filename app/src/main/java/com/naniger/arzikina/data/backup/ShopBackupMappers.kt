package com.naniger.arzikina.data.backup

import com.naniger.arzikina.data.local.entity.ProductCategoryEntity
import com.naniger.arzikina.data.local.entity.ProductEntity
import com.naniger.arzikina.data.local.entity.SaleEntity
import com.naniger.arzikina.data.local.entity.SaleLineEntity
import com.naniger.arzikina.data.local.entity.SalePaymentEntity
import com.naniger.arzikina.data.local.entity.ShopEntity
import com.naniger.arzikina.data.local.entity.StockMovementEntity

/**
 * Conversions Entity <-> DTO de sauvegarde pour la Boutique (même principes que `BackupMappers` : fonctions
 * pures, sans Room). `toEntity` insère toujours avec `id = 0L` et prend les nouvelles clés étrangères en
 * paramètre : ce sont les tables de correspondance ancien → nouvel id construites par [ShopBackupHandler]
 * pendant la restauration. Une clé OBLIGATOIRE absente de sa table (`getValue`) fait échouer — donc annuler —
 * toute la restauration plutôt que d'insérer une ligne orpheline ; une clé facultative absente devient `null`.
 *
 * `syncId` volontairement `null` à la restauration (comme les autres tables) : réutiliser celui du fichier
 * violerait l'index unique entre deux utilisateurs d'un même appareil, et les lignes sans `syncId` sont
 * justement celles que le rattrapage de la synchronisation enfile à l'envoi suivant.
 */
fun ShopEntity.toDto() = ShopDto(
    id = id, name = name, accountId = accountId, currencyCode = currencyCode,
    createdAt = createdAt, updatedAt = updatedAt
)

fun ShopDto.toEntity(userId: Long, accountIdMap: Map<Long, Long>) = ShopEntity(
    userId = userId, name = name, accountId = accountId?.let { accountIdMap[it] }, currencyCode = currencyCode,
    createdAt = createdAt, updatedAt = updatedAt
)

fun ProductCategoryEntity.toDto() = ProductCategoryDto(
    id = id, shopId = shopId, name = name, createdAt = createdAt, updatedAt = updatedAt
)

fun ProductCategoryDto.toEntity(userId: Long, shopIdMap: Map<Long, Long>) = ProductCategoryEntity(
    userId = userId, shopId = shopIdMap.getValue(shopId), name = name,
    createdAt = createdAt, updatedAt = updatedAt
)

/** [imageBase64] est lu par le gestionnaire (accès fichier), jamais ici : le mapper reste pur. */
fun ProductEntity.toDto(imageBase64: String?) = ProductDto(
    id = id, shopId = shopId, categoryId = categoryId, name = name, description = description, sku = sku,
    barcode = barcode, unit = unit, purchasePriceMinor = purchasePriceMinor, salePriceMinor = salePriceMinor,
    minStockMilli = minStockMilli, stockMilli = stockMilli, expiryDate = expiryDate, imageBase64 = imageBase64,
    isArchived = isArchived, createdAt = createdAt, updatedAt = updatedAt
)

/** [imageFileName] : nom du fichier réécrit sur le disque par le gestionnaire (`null` sans photo exploitable). */
fun ProductDto.toEntity(
    userId: Long,
    shopIdMap: Map<Long, Long>,
    categoryIdMap: Map<Long, Long>,
    imageFileName: String?
) = ProductEntity(
    userId = userId, shopId = shopIdMap.getValue(shopId), categoryId = categoryId?.let { categoryIdMap[it] },
    name = name, description = description, sku = sku, barcode = barcode, unit = unit,
    purchasePriceMinor = purchasePriceMinor, salePriceMinor = salePriceMinor, minStockMilli = minStockMilli,
    stockMilli = stockMilli, expiryDate = expiryDate, imageFileName = imageFileName, isArchived = isArchived,
    createdAt = createdAt, updatedAt = updatedAt
)

fun StockMovementEntity.toDto() = StockMovementDto(
    id = id, productId = productId, type = type, deltaMilli = deltaMilli, unitCostMinor = unitCostMinor,
    reason = reason, reference = reference, saleId = saleId, occurredAt = occurredAt,
    createdAt = createdAt, updatedAt = updatedAt
)

fun StockMovementDto.toEntity(userId: Long, productIdMap: Map<Long, Long>, saleIdMap: Map<Long, Long>) =
    StockMovementEntity(
        userId = userId, productId = productIdMap.getValue(productId), type = type, deltaMilli = deltaMilli,
        unitCostMinor = unitCostMinor, reason = reason, reference = reference,
        saleId = saleId?.let { saleIdMap[it] }, occurredAt = occurredAt,
        createdAt = createdAt, updatedAt = updatedAt
    )

fun SaleEntity.toDto() = SaleDto(
    id = id, shopId = shopId, reference = reference, soldAt = soldAt, currencyCode = currencyCode,
    customerName = customerName, customerPhone = customerPhone, subtotalMinor = subtotalMinor,
    discountMinor = discountMinor, totalMinor = totalMinor, status = status, cancelledAt = cancelledAt,
    cancelReason = cancelReason, createdAt = createdAt, updatedAt = updatedAt
)

/** `issuedReceiptId` reste `null` : voir la doc de [ShopBackupDto]. */
fun SaleDto.toEntity(userId: Long, shopIdMap: Map<Long, Long>) = SaleEntity(
    userId = userId, shopId = shopIdMap.getValue(shopId), reference = reference, soldAt = soldAt,
    currencyCode = currencyCode, customerName = customerName, customerPhone = customerPhone,
    subtotalMinor = subtotalMinor, discountMinor = discountMinor, totalMinor = totalMinor, status = status,
    cancelledAt = cancelledAt, cancelReason = cancelReason, issuedReceiptId = null,
    createdAt = createdAt, updatedAt = updatedAt
)

fun SaleLineEntity.toDto() = SaleLineDto(
    id = id, saleId = saleId, productId = productId, productName = productName, unitPriceMinor = unitPriceMinor,
    unitCostMinor = unitCostMinor, quantityMilli = quantityMilli, lineTotalMinor = lineTotalMinor,
    createdAt = createdAt, updatedAt = updatedAt
)

fun SaleLineDto.toEntity(userId: Long, saleIdMap: Map<Long, Long>, productIdMap: Map<Long, Long>) = SaleLineEntity(
    userId = userId, saleId = saleIdMap.getValue(saleId), productId = productIdMap.getValue(productId),
    productName = productName, unitPriceMinor = unitPriceMinor, unitCostMinor = unitCostMinor,
    quantityMilli = quantityMilli, lineTotalMinor = lineTotalMinor,
    createdAt = createdAt, updatedAt = updatedAt
)

fun SalePaymentEntity.toDto() = SalePaymentDto(
    id = id, saleId = saleId, accountId = accountId, method = method, amountMinor = amountMinor, paidAt = paidAt,
    transactionId = transactionId, reversalTransactionId = reversalTransactionId,
    createdAt = createdAt, updatedAt = updatedAt
)

fun SalePaymentDto.toEntity(
    userId: Long,
    saleIdMap: Map<Long, Long>,
    accountIdMap: Map<Long, Long>,
    transactionIdMap: Map<Long, Long>
) = SalePaymentEntity(
    userId = userId, saleId = saleIdMap.getValue(saleId), accountId = accountIdMap.getValue(accountId),
    method = method, amountMinor = amountMinor, paidAt = paidAt,
    transactionId = transactionId?.let { transactionIdMap[it] },
    reversalTransactionId = reversalTransactionId?.let { transactionIdMap[it] },
    createdAt = createdAt, updatedAt = updatedAt
)
