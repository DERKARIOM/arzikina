package com.naniger.arzikina.domain.shop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaleCartStoreTest {

    private val unit = StockRules.QUANTITY_UNIT_MILLI

    @Test
    fun `ajouter incremente d une unite et garde l ordre d ajout`() {
        val store = SaleCartStore()
        assertEquals(CartAddResult.ADDED, store.tryAdd(7, 5 * unit, false))
        assertEquals(CartAddResult.ADDED, store.tryAdd(3, 5 * unit, false))
        assertEquals(CartAddResult.ADDED, store.tryAdd(7, 5 * unit, false))
        assertEquals(listOf(7L, 3L), store.lines.value.keys.toList())
        assertEquals(2 * unit, store.lines.value[7L])
    }

    @Test
    fun `le stock limite l ajout sans modifier le panier`() {
        val store = SaleCartStore()
        assertEquals(CartAddResult.ADDED, store.tryAdd(1, unit, false))
        assertEquals(CartAddResult.STOCK_LIMIT, store.tryAdd(1, unit, false))
        assertEquals(unit, store.lines.value[1L])
    }

    @Test
    fun `produit epuise ou archive est refuse`() {
        val store = SaleCartStore()
        assertEquals(CartAddResult.OUT_OF_STOCK, store.tryAdd(1, 0L, false))
        assertEquals(CartAddResult.UNAVAILABLE, store.tryAdd(2, 10 * unit, true))
        assertTrue(store.lines.value.isEmpty())
    }

    @Test
    fun `quantite nulle retire la ligne`() {
        val store = SaleCartStore()
        store.setQuantity(4, 2_500L)
        assertEquals(2_500L, store.lines.value[4L])
        store.setQuantity(4, 0L)
        assertTrue(store.lines.value.isEmpty())
    }

    @Test
    fun `vider le panier change la cle d idempotence`() {
        val store = SaleCartStore()
        val before = store.draftKey
        store.tryAdd(1, 3 * unit, false)
        store.clear()
        assertTrue(store.lines.value.isEmpty())
        assertNotEquals(before, store.draftKey)
    }

    @Test
    fun `la cle reste stable tant que le panier n est pas valide`() {
        val store = SaleCartStore()
        val key = store.draftKey
        store.tryAdd(1, 3 * unit, false)
        store.remove(1)
        assertEquals(key, store.draftKey)
    }
}
