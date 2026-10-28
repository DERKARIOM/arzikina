package com.naniger.arzikina.domain.shop

import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.AccountIcon
import com.naniger.arzikina.domain.model.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopAccountPolicyTest {

    private fun account(id: Long, type: AccountType, currency: String = "XOF") = Account(
        id = id,
        name = "compte $id",
        icon = AccountIcon.CASH,
        colorArgb = 0L,
        currencyCode = currency,
        initialBalance = 0L,
        createdAt = 0L,
        type = type
    )

    @Test
    fun `seul le type Boutique peut encaisser`() {
        assertTrue(ShopAccountPolicy.canCollectSales(AccountType.BOUTIQUE))
        AccountType.entries.filter { it != AccountType.BOUTIQUE }.forEach {
            assertFalse("$it ne doit pas encaisser", ShopAccountPolicy.canCollectSales(it))
        }
    }

    @Test
    fun `les comptes proposes sont de type Boutique et de la devise de la boutique`() {
        val all = listOf(
            account(1, AccountType.CASH),
            account(2, AccountType.BOUTIQUE),
            account(3, AccountType.BOUTIQUE, currency = "EUR"),
            account(4, AccountType.MOBILE_MONEY),
            account(5, AccountType.BOUTIQUE)
        )
        assertEquals(listOf(2L, 5L), ShopAccountPolicy.eligibleAccounts(all, "XOF").map { it.id })
    }

    @Test
    fun `sans compte Boutique aucun compte n'est propose`() {
        val all = listOf(account(1, AccountType.CASH), account(2, AccountType.BANK))
        assertTrue(ShopAccountPolicy.eligibleAccounts(all, "XOF").isEmpty())
        assertNull(ShopAccountPolicy.pickDefault(emptyList(), chosenAccountId = 1L, shopAccountId = 2L))
    }

    @Test
    fun `la presélection suit le choix puis le compte par defaut puis le premier`() {
        val eligible = listOf(account(2, AccountType.BOUTIQUE), account(5, AccountType.BOUTIQUE))
        assertEquals(5L, ShopAccountPolicy.pickDefault(eligible, chosenAccountId = 5L, shopAccountId = 2L)?.id)
        assertEquals(5L, ShopAccountPolicy.pickDefault(eligible, chosenAccountId = null, shopAccountId = 5L)?.id)
        assertEquals(2L, ShopAccountPolicy.pickDefault(eligible, chosenAccountId = null, shopAccountId = null)?.id)
    }

    @Test
    fun `un ancien choix non eligible est ignore`() {
        // Ex. un compte Espèces choisi avant l'introduction de la règle : on retombe sur un compte Boutique.
        val eligible = listOf(account(2, AccountType.BOUTIQUE))
        assertEquals(2L, ShopAccountPolicy.pickDefault(eligible, chosenAccountId = 1L, shopAccountId = 1L)?.id)
    }
}
