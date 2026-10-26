package com.naniger.arzikina.data.backup

import com.naniger.arzikina.data.local.entity.ProductEntity
import com.naniger.arzikina.data.local.entity.SaleEntity
import com.naniger.arzikina.data.local.entity.SalePaymentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ShopBackupMappersTest {

    private fun product(id: Long = 10) = ProductEntity(
        id = id, userId = 1, shopId = 1, categoryId = 5, name = "Sucre", description = null, sku = "S1",
        barcode = "123", unit = "KG", purchasePriceMinor = 400, salePriceMinor = 500, minStockMilli = 2_000,
        stockMilli = 8_000, expiryDate = null, imageFileName = "abc.jpg", isArchived = false,
        createdAt = 1, updatedAt = 2, syncId = "old-sync", version = 7
    )

    @Test
    fun `produit exporte puis restaure garde ses valeurs et recoit de nouvelles cles`() {
        val dto = product().toDto(imageBase64 = "QUJD")
        assertEquals("QUJD", dto.imageBase64)
        val entity = dto.toEntity(userId = 9, shopIdMap = mapOf(1L to 77L), categoryIdMap = mapOf(5L to 55L), imageFileName = "new.jpg")
        assertEquals(0L, entity.id)
        assertEquals(9L, entity.userId)
        assertEquals(77L, entity.shopId)
        assertEquals(55L, entity.categoryId)
        assertEquals("new.jpg", entity.imageFileName)
        assertEquals(8_000L, entity.stockMilli)
        assertEquals("KG", entity.unit)
        assertNull(entity.syncId)
    }

    @Test
    fun `categorie absente de la correspondance devient null`() {
        val e = product().toDto(null).toEntity(1, mapOf(1L to 2L), emptyMap(), null)
        assertNull(e.categoryId)
    }

    @Test
    fun `boutique absente de la correspondance fait echouer la restauration`() {
        try {
            product().toDto(null).toEntity(1, emptyMap(), emptyMap(), null)
            fail("une cle obligatoire manquante doit lever")
        } catch (e: NoSuchElementException) {
            // attendu : la transaction Room serait annulee
        }
    }

    private fun sale() = SaleEntity(
        id = 3, userId = 1, shopId = 1, reference = "V-001", soldAt = 100, currencyCode = "XOF", customerName = "Awa",
        customerPhone = null, subtotalMinor = 1_000, discountMinor = 100, totalMinor = 900, status = "CANCELLED",
        cancelledAt = 200, cancelReason = "erreur", issuedReceiptId = 42, createdAt = 1, updatedAt = 2, syncId = "s", version = 3
    )

    @Test
    fun `la vente perd le lien vers le recu emis`() {
        val dto = sale().toDto()
        val entity = dto.toEntity(userId = 2, shopIdMap = mapOf(1L to 8L))
        assertNull(entity.issuedReceiptId)
        assertEquals("CANCELLED", entity.status)
        assertEquals(900L, entity.totalMinor)
        assertEquals(8L, entity.shopId)
    }

    @Test
    fun `paiement et contre-passation sont reliés aux nouvelles transactions et comptes`() {
        val payment = SalePaymentEntity(
            id = 1, userId = 1, saleId = 3, accountId = 4, method = "CASH", amountMinor = 900, paidAt = 100,
            transactionId = 11, reversalTransactionId = 12, createdAt = 1, updatedAt = 2, syncId = "p", version = 1
        )
        val e = payment.toDto().toEntity(
            userId = 5, saleIdMap = mapOf(3L to 30L), accountIdMap = mapOf(4L to 40L),
            transactionIdMap = mapOf(11L to 110L, 12L to 120L)
        )
        assertEquals(30L, e.saleId)
        assertEquals(40L, e.accountId)
        assertEquals(110L, e.transactionId)
        assertEquals(120L, e.reversalTransactionId)
    }

    @Test
    fun `paiement dont la transaction est inconnue garde le montant mais perd le lien`() {
        val payment = SalePaymentEntity(
            id = 1, userId = 1, saleId = 3, accountId = 4, method = "CASH", amountMinor = 900, paidAt = 100,
            transactionId = 11, reversalTransactionId = null, createdAt = 1, updatedAt = 2
        )
        val e = payment.toDto().toEntity(5, mapOf(3L to 30L), mapOf(4L to 40L), emptyMap())
        assertNull(e.transactionId)
        assertEquals(900L, e.amountMinor)
    }

    @Test
    fun `une sauvegarde sans boutique est un objet vide`() {
        val empty = ShopBackupDto()
        assertTrue(empty.shops.isEmpty() && empty.products.isEmpty() && empty.sales.isEmpty() && empty.salePayments.isEmpty())
    }
}
