package com.naniger.arzikina.data.shop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopSyncPolicyTest {

    @Test
    fun `ligne inconnue toujours appliquee`() {
        assertTrue(ShopSyncPolicy.shouldApply(allowCreate = true, localVersion = null, serverVersion = 1))
    }

    @Test
    fun `resultat d envoi applique meme a version egale`() {
        assertTrue(ShopSyncPolicy.shouldApply(allowCreate = false, localVersion = 1, serverVersion = 1))
    }

    @Test
    fun `pull n ecrase pas une ligne deja a jour`() {
        assertFalse(ShopSyncPolicy.shouldApply(allowCreate = true, localVersion = 3, serverVersion = 3))
        assertFalse(ShopSyncPolicy.shouldApply(allowCreate = true, localVersion = 4, serverVersion = 3))
    }

    @Test
    fun `pull applique un serveur plus recent`() {
        assertTrue(ShopSyncPolicy.shouldApply(allowCreate = true, localVersion = 3, serverVersion = 4))
    }

    @Test
    fun `reprise limitee dans le temps`() {
        val now = 1_000_000_000_000L
        assertTrue(ShopSyncPolicy.isDeferrable(now - 1_000, now))
        assertTrue(ShopSyncPolicy.isDeferrable(now - ShopSyncPolicy.DEFER_WINDOW_MILLIS, now))
        assertFalse(ShopSyncPolicy.isDeferrable(now - ShopSyncPolicy.DEFER_WINDOW_MILLIS - 1, now))
    }
}
