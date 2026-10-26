package com.naniger.arzikina.data.shop

import com.naniger.arzikina.data.local.entity.ProductEntity
import com.naniger.arzikina.data.local.entity.SaleEntity
import com.naniger.arzikina.data.local.entity.StockMovementEntity
import com.naniger.arzikina.data.remote.dto.ProductServerStateDto
import com.naniger.arzikina.data.remote.dto.ProductSyncPayload
import com.naniger.arzikina.data.remote.dto.SalePaymentServerStateDto
import com.naniger.arzikina.domain.model.SyncOperation
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopSyncMapperTest {

    private val product = ProductEntity(
        id = 5, userId = 1, shopId = 2, categoryId = 3, name = "Riz", description = null, sku = "R1",
        barcode = null, unit = "kg", purchasePriceMinor = 400, salePriceMinor = 500, minStockMilli = 1000,
        stockMilli = 7000, expiryDate = null, imageFileName = "a.jpg", isArchived = true,
        createdAt = 10, updatedAt = 20, syncId = "p-1", version = 4
    )

    private val JSON = Json { encodeDefaults = true }

    @Test
    fun `creation sans baseVersion et mise a jour avec la version connue`() {
        val create = ShopSyncMapper.toPayload(product, "p-1", SyncOperation.CREATE, "s-1", "c-1")
        val update = ShopSyncMapper.toPayload(product, "p-1", SyncOperation.UPDATE, "s-1", null)
        assertNull(create.baseVersion)
        assertEquals(4, update.baseVersion)
        assertNull(update.categorySyncId)
    }

    @Test
    fun `payload produit ne transporte ni stock ni image mais les cles etrangeres en syncId`() {
        val p = ShopSyncMapper.toPayload(product, "p-1", SyncOperation.CREATE, "s-1", "c-1")
        assertEquals("s-1", p.shopSyncId)
        assertEquals("c-1", p.categorySyncId)
        assertTrue(p.isArchived)
        val json = JSON.encodeToString(ProductSyncPayload.serializer(), p)
        assertFalse(json.contains("stockMilli"))
        assertFalse(json.contains("imageFileName"))
        assertTrue(json.contains("\"minStockMilli\""))
    }

    @Test
    fun `reception produit conserve stock et image locaux`() {
        val state = ProductServerStateDto(
            id = "p-1", shopSyncId = "s-1", name = "Riz 2", unit = "kg", salePriceMinor = 600,
            minStockMilli = 0, isArchived = 0, createdAt = 10, updatedAt = 30, version = 5
        )
        val e = ShopSyncMapper.toEntity(state, 1, product, shopId = 2, categoryId = null)
        assertEquals(5L, e.id)
        assertEquals(7000L, e.stockMilli)
        assertEquals("a.jpg", e.imageFileName)
        assertFalse(e.isArchived)
        assertEquals(5, e.version)
        assertEquals("p-1", e.syncId)
    }

    @Test
    fun `reception creation produit demarre a stock zero sans image`() {
        val state = ProductServerStateDto(
            id = "p-9", shopSyncId = "s-1", name = "X", unit = "u", salePriceMinor = 1,
            minStockMilli = 0, isArchived = 1, createdAt = 1, updatedAt = 1, version = 1
        )
        val e = ShopSyncMapper.toEntity(state, 1, null, shopId = 2, categoryId = 3)
        assertEquals(0L, e.id)
        assertEquals(0L, e.stockMilli)
        assertNull(e.imageFileName)
        assertTrue(e.isArchived)
    }

    @Test
    fun `etat serveur accepte isArchived entier et ignore les champs inconnus`() {
        val json = Json { ignoreUnknownKeys = true }
        val s = json.decodeFromString(
            ProductServerStateDto.serializer(),
            """{"id":"p","userId":9,"shopSyncId":"s","name":"n","unit":"u","salePriceMinor":1,"minStockMilli":0,
               "isArchived":1,"createdAt":1,"updatedAt":2,"deletedAt":null,"version":3}"""
        )
        assertEquals(1, s.isArchived)
        assertNull(s.categorySyncId)
    }

    @Test
    fun `mouvement et vente conservent leurs valeurs et references`() {
        val m = StockMovementEntity(
            id = 1, userId = 1, productId = 5, type = "SALE", deltaMilli = -2000, unitCostMinor = 400,
            reason = null, reference = "V1", saleId = 9, occurredAt = 99, createdAt = 99, updatedAt = 99,
            syncId = "m-1"
        )
        val mp = ShopSyncMapper.toPayload(m, "m-1", SyncOperation.CREATE, "p-1", "sale-1")
        assertEquals(-2000L, mp.deltaMilli)
        assertEquals("sale-1", mp.saleSyncId)

        val sale = SaleEntity(
            id = 9, userId = 1, shopId = 2, reference = "V1", soldAt = 1, currencyCode = "XOF",
            customerName = null, customerPhone = null, subtotalMinor = 10, discountMinor = 0, totalMinor = 10,
            status = "CANCELLED", cancelledAt = 5, cancelReason = "erreur", issuedReceiptId = 4,
            createdAt = 1, updatedAt = 5, syncId = "sale-1", version = 2
        )
        val sp = ShopSyncMapper.toPayload(sale, "sale-1", SyncOperation.UPDATE, "s-1", "r-1")
        assertEquals(2, sp.baseVersion)
        assertEquals("CANCELLED", sp.status)
        assertEquals("r-1", sp.issuedReceiptSyncId)
    }

    @Test
    fun `reception paiement relie vente compte et transactions resolus`() {
        val state = SalePaymentServerStateDto(
            id = "pay", saleSyncId = "sale-1", accountSyncId = "a-1", method = "CASH", amountMinor = 10,
            paidAt = 1, transactionSyncId = "t-1", reversalTransactionSyncId = null,
            createdAt = 1, updatedAt = 2, version = 2
        )
        val e = ShopSyncMapper.toEntity(state, 1, null, saleId = 9, accountId = 3, transactionId = 11, reversalTransactionId = null)
        assertEquals(9L, e.saleId)
        assertEquals(3L, e.accountId)
        assertEquals(11L, e.transactionId)
        assertNull(e.reversalTransactionId)
    }

    @Test
    fun `syncId de boutique deterministe par compte serveur`() {
        val a1 = ShopSyncMapper.shopSyncId("42")
        assertEquals(a1, ShopSyncMapper.shopSyncId("42"))
        assertFalse(a1 == ShopSyncMapper.shopSyncId("43"))
    }
}
