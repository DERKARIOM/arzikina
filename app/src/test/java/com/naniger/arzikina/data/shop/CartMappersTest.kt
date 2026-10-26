package com.naniger.arzikina.data.shop

import com.naniger.arzikina.data.local.entity.CartDraftEntity
import com.naniger.arzikina.data.local.entity.CartLineEntity
import com.naniger.arzikina.data.repository.ShopScope
import com.naniger.arzikina.domain.shop.CartSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CartMappersTest {

    private val scope = ShopScope(userId = 4L, shopId = 9L)

    private fun line(productId: Long, quantity: Long, position: Int) =
        CartLineEntity(scope.userId, scope.shopId, productId, quantity, position)

    @Test
    fun `sans brouillon on obtient un panier vide neuf`() {
        val snapshot = toCartSnapshot(null, listOf(line(1L, 1000L, 0)))
        assertTrue(snapshot.lines.isEmpty())
        assertTrue(snapshot.draftKey.isNotBlank())
    }

    @Test
    fun `une cle vide ou sans ligne valide donne un panier neuf`() {
        val blank = CartDraftEntity(4L, 9L, " ", 1L)
        assertTrue(toCartSnapshot(blank, listOf(line(1L, 1000L, 0))).lines.isEmpty())
        val draft = CartDraftEntity(4L, 9L, "K", 1L)
        val empty = toCartSnapshot(draft, listOf(line(1L, 0L, 0), line(2L, -5L, 1)))
        assertTrue(empty.lines.isEmpty())
        assertNotEquals("K", empty.draftKey)
    }

    @Test
    fun `les lignes sont remises dans l'ordre d'ajout et la cle est conservee`() {
        val draft = CartDraftEntity(4L, 9L, "K", 1L)
        val snapshot = toCartSnapshot(draft, listOf(line(7L, 3000L, 2), line(5L, 1000L, 0), line(6L, 2500L, 1)))
        assertEquals("K", snapshot.draftKey)
        assertEquals(listOf(5L, 6L, 7L), snapshot.lines.keys.toList())
        assertEquals(2500L, snapshot.lines[6L])
    }

    @Test
    fun `l'aller retour conserve le panier`() {
        val original = CartSnapshot(linkedMapOf(8L to 1500L, 2L to 1000L), "KEY")
        val lines = original.toLineEntities(scope)
        assertEquals(listOf(0, 1), lines.map { it.position })
        assertTrue(lines.all { it.userId == 4L && it.shopId == 9L })
        val draft = original.toDraftEntity(scope, now = 123L)
        assertEquals(CartDraftEntity(4L, 9L, "KEY", 123L), draft)
        assertEquals(original, toCartSnapshot(draft, lines.reversed()))
    }
}
