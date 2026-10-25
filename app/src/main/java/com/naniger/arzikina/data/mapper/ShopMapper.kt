package com.naniger.arzikina.data.mapper

import com.naniger.arzikina.data.local.dao.ProductSummaryRow
import com.naniger.arzikina.data.local.dao.ShopStatsRow
import com.naniger.arzikina.data.local.entity.ProductCategoryEntity
import com.naniger.arzikina.data.local.entity.ProductEntity
import com.naniger.arzikina.data.local.entity.ShopEntity
import com.naniger.arzikina.data.local.entity.StockMovementEntity
import com.naniger.arzikina.domain.shop.Product
import com.naniger.arzikina.domain.shop.ProductImageFiles
import com.naniger.arzikina.domain.shop.ProductCategory
import com.naniger.arzikina.domain.shop.ProductSummary
import com.naniger.arzikina.domain.shop.ProductUnit
import com.naniger.arzikina.domain.shop.Shop
import com.naniger.arzikina.domain.shop.ShopStats
import com.naniger.arzikina.domain.shop.StockMovement
import com.naniger.arzikina.domain.shop.StockMovementType

/** Conversions entité ↔ domaine du module Boutique (aucune logique métier ici). */

fun ShopEntity.toDomain() = Shop(id = id, name = name, currencyCode = currencyCode, accountId = accountId)

fun ProductCategoryEntity.toDomain() = ProductCategory(id = id, name = name)

/** [images] résout le nom de fichier stocké en fichier réel (absent si la photo a disparu). */
fun ProductEntity.toDomain(images: ProductImageFiles) = Product(
    id = id,
    shopId = shopId,
    categoryId = categoryId,
    name = name,
    description = description,
    sku = sku,
    barcode = barcode,
    unit = ProductUnit.fromStored(unit),
    purchasePriceMinor = purchasePriceMinor,
    salePriceMinor = salePriceMinor,
    minStockMilli = minStockMilli,
    stockMilli = stockMilli,
    expiryDate = expiryDate,
    isArchived = isArchived,
    createdAt = createdAt,
    imageFile = images.resolve(imageFileName)
)

fun ProductSummaryRow.toDomain(images: ProductImageFiles) = ProductSummary(
    id = id,
    name = name,
    sku = sku,
    barcode = barcode,
    unit = ProductUnit.fromStored(unit),
    salePriceMinor = salePriceMinor,
    stockMilli = stockMilli,
    minStockMilli = minStockMilli,
    categoryId = categoryId,
    isArchived = isArchived,
    imageFile = images.resolve(imageFileName)
)

/** La valeur d'achat n'a de sens que si au moins un produit en stock a un prix d'achat connu. */
fun ShopStatsRow.toDomain() = ShopStats(
    totalProducts = totalProducts,
    inStock = inStock,
    lowStock = lowStock,
    outOfStock = outOfStock,
    stockValueAtCostMinor = if (productsWithCost > 0) stockValueAtCostMinor else null,
    productsWithoutCost = productsWithoutCost,
    stockValueAtSaleMinor = stockValueAtSaleMinor
)

fun StockMovementEntity.toDomain() = StockMovement(
    id = id,
    productId = productId,
    type = StockMovementType.fromStored(type),
    deltaMilli = deltaMilli,
    unitCostMinor = unitCostMinor,
    reason = reason,
    reference = reference,
    saleId = saleId,
    occurredAt = occurredAt
)
