package com.naniger.arzikina.domain.shop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Publication atomique des lignes et de la clé du brouillon (base de la persistance du panier). */
class SaleCartStoreSnapshotTest {

    private val unit = StockRules.QUANTITY_UNIT_MILLI

    @Test
    fun `un panier neuf est vide avec une cle`() {
        val store = SaleCartStore()
        assertTrue(store.lines.value.isEmpty())
        assertTrue(store.draftKey.isNotBlank())
        assertEquals(store.draftKey, store.snapshot.value.draftKey)
    }

    @Test
    fun `l'ajout publie lignes et snapshot ensemble`() {
        val store = SaleCartStore()
        assertEquals(CartAddResult.ADDED, store.tryAdd(1L, 5 * unit, isArchived = false))
        assertEquals(mapOf(1L to unit), store.lines.value)
        assertEquals(store.lines.value, store.snapshot.value.lines)
    }

    @Test
    fun `un refus ne change ni les lignes ni le snapshot`() {
        val store = SaleCartStore()
        store.tryAdd(1L, unit, isArchived = false)
        val before = store.snapshot.value
        assertEquals(CartAddResult.STOCK_LIMIT, store.tryAdd(1L, unit, isArchived = false))
        assertEquals(CartAddResult.OUT_OF_STOCK, store.tryAdd(2L, 0L, isArchived = false))
        assertEquals(CartAddResult.UNAVAILABLE, store.tryAdd(3L, 9 * unit, isArchived = true))
        assertEquals(before, store.snapshot.value)
    }

    @Test
    fun `l'ordre d'ajout est conserve et une quantite modifiee garde sa place`() {
        val store = SaleCartStore()
        store.tryAdd(3L, 9 * unit, false)
        store.tryAdd(1L, 9 * unit, false)
        store.tryAdd(2L, 9 * unit, false)
        store.setQuantity(3L, 4 * unit)
        assertEquals(listOf(3L, 1L, 2L), store.lines.value.keys.toList())
        assertEquals(4 * unit, store.lines.value[3L])
    }

    @Test
    fun `une quantite nulle retire la ligne`() {
        val store = SaleCartStore()
        store.tryAdd(1L, 9 * unit, false)
        store.setQuantity(1L, 0L)
        assertTrue(store.lines.value.isEmpty())
        assertTrue(store.snapshot.value.lines.isEmpty())
    }

    @Test
    fun `clear vide le panier et change la cle`() {
        val store = SaleCartStore()
        store.tryAdd(1L, 9 * unit, false)
        val oldKey = store.draftKey
        store.clear()
        assertTrue(store.lines.value.isEmpty())
        assertNotEquals(oldKey, store.draftKey)
    }

    @Test
    fun `retirer une ligne garde la meme cle`() {
        val store = SaleCartStore()
        store.tryAdd(1L, 9 * unit, false)
        val key = store.draftKey
        store.remove(1L)
        assertEquals(key, store.draftKey)
    }

    @Test
    fun `replaceWith restaure lignes et cle ensemble`() {
        val store = SaleCartStore()
        val restored = CartSnapshot(linkedMapOf(7L to 2 * unit, 5L to unit), "cle-restauree")
        store.replaceWith(restored)
        assertEquals("cle-restauree", store.draftKey)
        assertEquals(listOf(7L, 5L), store.lines.value.keys.toList())
        assertEquals(restored, store.snapshot.value)
    }

    @Test
    fun `fresh genere des cles distinctes`() {
        assertNotEquals(CartSnapshot.fresh().draftKey, CartSnapshot.fresh().draftKey)
    }
}
