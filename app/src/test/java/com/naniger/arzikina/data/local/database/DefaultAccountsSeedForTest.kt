package com.naniger.arzikina.data.local.database

import com.naniger.arzikina.domain.model.AccountIcon
import com.naniger.arzikina.domain.model.AccountType
import com.naniger.arzikina.domain.model.DefaultAccountKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultAccountsSeedForTest {

    @Test
    fun `le compte Boutique est de type BOUTIQUE avec un solde initial nul`() {
        val account = DefaultAccounts.seedFor(1L, 7L, listOf(DefaultAccountKey.SHOP)).single()
        assertEquals(AccountType.BOUTIQUE, account.type)
        assertEquals(AccountIcon.WALLET, account.icon)
        assertEquals(DefaultAccountKey.SHOP.canonicalName, account.name)
        assertEquals(0L, account.initialBalanceMinor)
        assertEquals(7L, account.userId)
        assertNull(account.syncId)
    }

    @Test
    fun `le compte Especes reprend la definition historique avec un solde nul`() {
        val account = DefaultAccounts.seedFor(1L, 7L, listOf(DefaultAccountKey.CASH)).single()
        assertEquals(AccountType.CASH, account.type)
        assertEquals(DefaultAccountKey.CASH.canonicalName, account.name)
        assertEquals(0L, account.initialBalanceMinor)
    }

    @Test
    fun `les deux comptes sont independants et ordonnes apres les comptes existants`() {
        val accounts = DefaultAccounts.seedFor(1L, 7L, listOf(DefaultAccountKey.SHOP, DefaultAccountKey.CASH), firstDisplayOrder = 3L)
        assertEquals(listOf(3L, 4L), accounts.map { it.displayOrder })
        assertEquals(2, accounts.map { it.name }.toSet().size)
        assertEquals(setOf(7L), accounts.map { it.userId }.toSet())
    }

    @Test
    fun `l exclusion des statistiques ne concerne que le compte Boutique`() {
        val accounts = DefaultAccounts.seedFor(
            1L, 7L, listOf(DefaultAccountKey.SHOP, DefaultAccountKey.CASH), excludeShopFromStatistics = true
        )
        assertEquals(listOf(true, false), accounts.map { it.isExcludedFromStatistics })
        val plain = DefaultAccounts.seedFor(1L, 7L, listOf(DefaultAccountKey.SHOP, DefaultAccountKey.CASH))
        assertEquals(listOf(false, false), plain.map { it.isExcludedFromStatistics })
    }

    @Test
    fun `aucune cle demandee ne cree aucun compte`() {
        assertEquals(emptyList<Any>(), DefaultAccounts.seedFor(1L, 7L, emptyList()))
    }
}
