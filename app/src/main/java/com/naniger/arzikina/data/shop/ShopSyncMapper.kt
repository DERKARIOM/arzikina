package com.naniger.arzikina.data.shop

import com.naniger.arzikina.data.local.entity.ProductCategoryEntity
import com.naniger.arzikina.data.local.entity.ProductEntity
import com.naniger.arzikina.data.local.entity.SaleEntity
import com.naniger.arzikina.data.local.entity.SaleLineEntity
import com.naniger.arzikina.data.local.entity.SalePaymentEntity
import com.naniger.arzikina.data.local.entity.ShopEntity
import com.naniger.arzikina.data.local.entity.StockMovementEntity
import com.naniger.arzikina.data.remote.dto.ProductCategoryServerStateDto
import com.naniger.arzikina.data.remote.dto.ProductCategorySyncPayload
import com.naniger.arzikina.data.remote.dto.ProductServerStateDto
import com.naniger.arzikina.data.remote.dto.ProductSyncPayload
import com.naniger.arzikina.data.remote.dto.SaleLineServerStateDto
import com.naniger.arzikina.data.remote.dto.SaleLineSyncPayload
import com.naniger.arzikina.data.remote.dto.SalePaymentServerStateDto
import com.naniger.arzikina.data.remote.dto.SalePaymentSyncPayload
import com.naniger.arzikina.data.remote.dto.SaleServerStateDto
import com.naniger.arzikina.data.remote.dto.SaleSyncPayload
import com.naniger.arzikina.data.remote.dto.ShopServerStateDto
import com.naniger.arzikina.data.remote.dto.ShopSyncPayload
import com.naniger.arzikina.data.remote.dto.StockMovementServerStateDto
import com.naniger.arzikina.data.remote.dto.StockMovementSyncPayload
import com.naniger.arzikina.domain.model.SyncOperation
import com.naniger.arzikina.domain.shop.ProductImagePolicy
import java.util.UUID

/**
 * Conversions PURES (sans Room ni réseau) entre entités Boutique, charges utiles sortantes et états
 * serveur entrants.
 *
 * Les clés étrangères ne sont PAS résolues ici : l'appelant (le gestionnaire de synchro, qui a accès aux
 * DAO) fournit
 *  - à l'envoi, les `syncId` des lignes référencées ;
 *  - à la réception, les ids Room locaux correspondants (voir `SYNC-BOUTIQUE-CONTRAT.md`).
 * Ce découpage garde le mapper testable sans base de données.
 *
 * À la réception, le cache de stock ([ProductEntity.stockMilli]) est conservé depuis la ligne existante puis
 * recalculé depuis les mouvements par l'appelant ; le nom de la photo suit [ProductImagePolicy.resolveReceivedName]
 * (le fichier lui-même est transféré à part, voir `ProductImageSyncHandler`).
 */
internal object ShopSyncMapper {

    /**
     * `syncId` DÉTERMINISTE de la boutique : la V1 n'a qu'une boutique par compte, donc tous les appareils
     * d'un même compte doivent viser la même ligne serveur, sans coordination (même principe que le profil
     * émetteur). Évite deux boutiques « par défaut » créées hors ligne sur deux appareils.
     */
    fun shopSyncId(serverUserId: String): String =
        UUID.nameUUIDFromBytes("arzikina:shop:$serverUserId".toByteArray(Charsets.UTF_8)).toString()

    /** `null` pour une création, sinon la version connue (détection de conflit côté serveur). */
    fun baseVersionFor(operation: SyncOperation, version: Int): Int? =
        if (operation == SyncOperation.CREATE) null else version

    // ---- Envoi -------------------------------------------------------------------------------

    fun toPayload(e: ShopEntity, syncId: String, op: SyncOperation, accountSyncId: String?) =
        ShopSyncPayload(
            id = syncId, baseVersion = baseVersionFor(op, e.version), name = e.name,
            accountSyncId = accountSyncId, currencyCode = e.currencyCode,
            createdAt = e.createdAt, updatedAt = e.updatedAt
        )

    fun toPayload(e: ProductCategoryEntity, syncId: String, op: SyncOperation, shopSyncId: String) =
        ProductCategorySyncPayload(
            id = syncId, baseVersion = baseVersionFor(op, e.version), shopSyncId = shopSyncId,
            name = e.name, createdAt = e.createdAt, updatedAt = e.updatedAt
        )

    fun toPayload(
        e: ProductEntity, syncId: String, op: SyncOperation, shopSyncId: String, categorySyncId: String?
    ) = ProductSyncPayload(
        id = syncId, baseVersion = baseVersionFor(op, e.version), shopSyncId = shopSyncId,
        categorySyncId = categorySyncId, name = e.name, description = e.description, sku = e.sku,
        barcode = e.barcode, unit = e.unit, purchasePriceMinor = e.purchasePriceMinor,
        salePriceMinor = e.salePriceMinor, minStockMilli = e.minStockMilli, expiryDate = e.expiryDate,
        imageFileName = e.imageFileName, isArchived = e.isArchived, createdAt = e.createdAt, updatedAt = e.updatedAt
    )

    fun toPayload(
        e: StockMovementEntity, syncId: String, op: SyncOperation, productSyncId: String, saleSyncId: String?
    ) = StockMovementSyncPayload(
        id = syncId, baseVersion = baseVersionFor(op, e.version), productSyncId = productSyncId,
        type = e.type, deltaMilli = e.deltaMilli, unitCostMinor = e.unitCostMinor, reason = e.reason,
        reference = e.reference, saleSyncId = saleSyncId, occurredAt = e.occurredAt,
        createdAt = e.createdAt, updatedAt = e.updatedAt
    )

    fun toPayload(
        e: SaleEntity, syncId: String, op: SyncOperation, shopSyncId: String, issuedReceiptSyncId: String?
    ) = SaleSyncPayload(
        id = syncId, baseVersion = baseVersionFor(op, e.version), shopSyncId = shopSyncId,
        reference = e.reference, soldAt = e.soldAt, currencyCode = e.currencyCode,
        customerName = e.customerName, customerPhone = e.customerPhone,
        subtotalMinor = e.subtotalMinor, discountMinor = e.discountMinor, totalMinor = e.totalMinor,
        status = e.status, cancelledAt = e.cancelledAt, cancelReason = e.cancelReason,
        issuedReceiptSyncId = issuedReceiptSyncId, createdAt = e.createdAt, updatedAt = e.updatedAt
    )

    fun toPayload(
        e: SaleLineEntity, syncId: String, op: SyncOperation, saleSyncId: String, productSyncId: String
    ) = SaleLineSyncPayload(
        id = syncId, baseVersion = baseVersionFor(op, e.version), saleSyncId = saleSyncId,
        productSyncId = productSyncId, productName = e.productName, unitPriceMinor = e.unitPriceMinor,
        unitCostMinor = e.unitCostMinor, quantityMilli = e.quantityMilli, lineTotalMinor = e.lineTotalMinor,
        createdAt = e.createdAt, updatedAt = e.updatedAt
    )

    fun toPayload(
        e: SalePaymentEntity, syncId: String, op: SyncOperation, saleSyncId: String, accountSyncId: String,
        transactionSyncId: String?, reversalTransactionSyncId: String?
    ) = SalePaymentSyncPayload(
        id = syncId, baseVersion = baseVersionFor(op, e.version), saleSyncId = saleSyncId,
        accountSyncId = accountSyncId, method = e.method, amountMinor = e.amountMinor, paidAt = e.paidAt,
        transactionSyncId = transactionSyncId, reversalTransactionSyncId = reversalTransactionSyncId,
        createdAt = e.createdAt, updatedAt = e.updatedAt
    )

    // ---- Réception ---------------------------------------------------------------------------
    // [local] : ligne existante (conserve l'id Room), `null` pour une création.

    fun toEntity(s: ShopServerStateDto, userId: Long, local: ShopEntity?, accountId: Long?) = ShopEntity(
        id = local?.id ?: 0L, userId = userId, name = s.name, accountId = accountId,
        currencyCode = s.currencyCode, createdAt = s.createdAt, updatedAt = s.updatedAt,
        syncId = s.id, deletedAt = s.deletedAt, version = s.version
    )

    fun toEntity(s: ProductCategoryServerStateDto, userId: Long, local: ProductCategoryEntity?, shopId: Long) =
        ProductCategoryEntity(
            id = local?.id ?: 0L, userId = userId, shopId = shopId, name = s.name,
            createdAt = s.createdAt, updatedAt = s.updatedAt, syncId = s.id,
            deletedAt = s.deletedAt, version = s.version
        )

    /** [serverAuthoritativeImage] : voir [ProductImagePolicy.resolveReceivedName]. */
    fun toEntity(
        s: ProductServerStateDto, userId: Long, local: ProductEntity?, shopId: Long, categoryId: Long?,
        serverAuthoritativeImage: Boolean = false
    ) = ProductEntity(
        id = local?.id ?: 0L, userId = userId, shopId = shopId, categoryId = categoryId, name = s.name,
        description = s.description, sku = s.sku, barcode = s.barcode, unit = s.unit,
        purchasePriceMinor = s.purchasePriceMinor, salePriceMinor = s.salePriceMinor,
        minStockMilli = s.minStockMilli,
        stockMilli = local?.stockMilli ?: 0L,          // cache : recalculé depuis les mouvements
        expiryDate = s.expiryDate,
        imageFileName = ProductImagePolicy.resolveReceivedName(s.imageFileName, local?.imageFileName, serverAuthoritativeImage),
        isArchived = s.isArchived != 0,
        createdAt = s.createdAt, updatedAt = s.updatedAt, syncId = s.id,
        deletedAt = s.deletedAt, version = s.version
    )

    fun toEntity(
        s: StockMovementServerStateDto, userId: Long, local: StockMovementEntity?, productId: Long, saleId: Long?
    ) = StockMovementEntity(
        id = local?.id ?: 0L, userId = userId, productId = productId, type = s.type,
        deltaMilli = s.deltaMilli, unitCostMinor = s.unitCostMinor, reason = s.reason,
        reference = s.reference, saleId = saleId, occurredAt = s.occurredAt,
        createdAt = s.createdAt, updatedAt = s.updatedAt, syncId = s.id,
        deletedAt = s.deletedAt, version = s.version
    )

    fun toEntity(
        s: SaleServerStateDto, userId: Long, local: SaleEntity?, shopId: Long, issuedReceiptId: Long?
    ) = SaleEntity(
        id = local?.id ?: 0L, userId = userId, shopId = shopId, reference = s.reference, soldAt = s.soldAt,
        currencyCode = s.currencyCode, customerName = s.customerName, customerPhone = s.customerPhone,
        subtotalMinor = s.subtotalMinor, discountMinor = s.discountMinor, totalMinor = s.totalMinor,
        status = s.status, cancelledAt = s.cancelledAt, cancelReason = s.cancelReason,
        issuedReceiptId = issuedReceiptId, createdAt = s.createdAt, updatedAt = s.updatedAt,
        syncId = s.id, deletedAt = s.deletedAt, version = s.version
    )

    fun toEntity(
        s: SaleLineServerStateDto, userId: Long, local: SaleLineEntity?, saleId: Long, productId: Long
    ) = SaleLineEntity(
        id = local?.id ?: 0L, userId = userId, saleId = saleId, productId = productId,
        productName = s.productName, unitPriceMinor = s.unitPriceMinor, unitCostMinor = s.unitCostMinor,
        quantityMilli = s.quantityMilli, lineTotalMinor = s.lineTotalMinor,
        createdAt = s.createdAt, updatedAt = s.updatedAt, syncId = s.id,
        deletedAt = s.deletedAt, version = s.version
    )

    fun toEntity(
        s: SalePaymentServerStateDto, userId: Long, local: SalePaymentEntity?, saleId: Long, accountId: Long,
        transactionId: Long?, reversalTransactionId: Long?
    ) = SalePaymentEntity(
        id = local?.id ?: 0L, userId = userId, saleId = saleId, accountId = accountId, method = s.method,
        amountMinor = s.amountMinor, paidAt = s.paidAt, transactionId = transactionId,
        reversalTransactionId = reversalTransactionId, createdAt = s.createdAt, updatedAt = s.updatedAt,
        syncId = s.id, deletedAt = s.deletedAt, version = s.version
    )
}
