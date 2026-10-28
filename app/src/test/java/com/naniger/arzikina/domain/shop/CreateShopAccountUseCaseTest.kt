package com.naniger.arzikina.domain.shop

import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.AccountIcon
import com.naniger.arzikina.domain.model.AccountType
import com.naniger.arzikina.domain.model.CardSecrets
import com.naniger.arzikina.domain.repository.AccountRepository
import com.naniger.arzikina.domain.repository.ShopRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateShopAccountUseCaseTest {

    private class FakeAccounts(initial: List<Account> = emptyList()) : AccountRepository {
        val state = MutableStateFlow(initial)
        val saved = mutableListOf<Account>()
        override fun observeAccounts(): Flow<List<Account>> = state
        override suspend fun getAccount(id: Long): Account? = state.value.firstOrNull { it.id == id }
        override suspend fun saveAccount(account: Account): Long {
            saved += account
            val id = 100L + saved.size
            state.value = state.value + account.copy(id = id)
            return id
        }
        override suspend fun deleteAccount(id: Long) = Unit
        override suspend fun reorderAccounts(orderedIds: List<Long>) = Unit
        override suspend fun saveCardSecrets(accountId: Long, fullNumber: String, cvv: String) = Unit
        override suspend fun revealCardSecrets(accountId: Long): CardSecrets? = null
    }

    private class FakeShops(shop: Shop?) : ShopRepository {
        private val state = MutableStateFlow(shop)
        override fun observeShop(): Flow<Shop?> = state
        override suspend fun getOrCreateShop(defaultName: String): Shop = state.value!!
    }

    private val shop = Shop(id = 1, name = "Boutique", currencyCode = "XOF", accountId = null)

    private fun existing(name: String) = Account(
        id = 1, name = name, icon = AccountIcon.CASH, colorArgb = 0L, currencyCode = "XOF",
        initialBalance = 0L, createdAt = 0L, type = AccountType.CASH
    )

    @Test
    fun `cree un compte Boutique dans la devise de la boutique avec un solde nul`() = runTest {
        val accounts = FakeAccounts()
        val result = CreateShopAccountUseCase(accounts, FakeShops(shop))("  Caisse boutique ")

        val created = result as CreateShopAccountResult.Created
        assertEquals("Caisse boutique", created.name)
        val saved = accounts.saved.single()
        assertEquals(AccountType.BOUTIQUE, saved.type)
        assertEquals("XOF", saved.currencyCode)
        assertEquals(0L, saved.initialBalance)
        assertEquals(0L, saved.id) // création : l'id est attribué par le dépôt
    }

    @Test
    fun `le compte cree est eligible a l'encaissement`() = runTest {
        val accounts = FakeAccounts()
        CreateShopAccountUseCase(accounts, FakeShops(shop))("Caisse")
        val eligible = ShopAccountPolicy.eligibleAccounts(accounts.state.value, "XOF")
        assertEquals(1, eligible.size)
    }

    @Test
    fun `refuse un nom vide`() = runTest {
        val accounts = FakeAccounts()
        val result = CreateShopAccountUseCase(accounts, FakeShops(shop))("   ")
        assertEquals(CreateShopAccountResult.Rejected(CreateShopAccountError.NAME_REQUIRED), result)
        assertTrue(accounts.saved.isEmpty())
    }

    @Test
    fun `refuse un nom trop long`() = runTest {
        val accounts = FakeAccounts()
        val tooLong = "a".repeat(CreateShopAccountUseCase.MAX_NAME_LENGTH + 1)
        val result = CreateShopAccountUseCase(accounts, FakeShops(shop))(tooLong)
        assertEquals(CreateShopAccountResult.Rejected(CreateShopAccountError.NAME_TOO_LONG), result)
        assertTrue(accounts.saved.isEmpty())
    }

    @Test
    fun `accepte exactement la longueur maximale`() = runTest {
        val accounts = FakeAccounts()
        val name = "a".repeat(CreateShopAccountUseCase.MAX_NAME_LENGTH)
        assertTrue(CreateShopAccountUseCase(accounts, FakeShops(shop))(name) is CreateShopAccountResult.Created)
    }

    @Test
    fun `refuse un nom deja utilise sans tenir compte de la casse`() = runTest {
        val accounts = FakeAccounts(listOf(existing("Espèces")))
        val result = CreateShopAccountUseCase(accounts, FakeShops(shop))("espèces")
        assertEquals(CreateShopAccountResult.Rejected(CreateShopAccountError.NAME_DUPLICATE), result)
        assertTrue(accounts.saved.isEmpty())
    }

    @Test
    fun `un second appel avec le meme nom est refuse comme doublon`() = runTest {
        val accounts = FakeAccounts()
        val useCase = CreateShopAccountUseCase(accounts, FakeShops(shop))
        assertTrue(useCase("Caisse") is CreateShopAccountResult.Created)
        assertEquals(CreateShopAccountResult.Rejected(CreateShopAccountError.NAME_DUPLICATE), useCase("Caisse"))
        assertEquals(1, accounts.saved.size)
    }

    @Test
    fun `refuse sans boutique`() = runTest {
        val accounts = FakeAccounts()
        val result = CreateShopAccountUseCase(accounts, FakeShops(null))("Caisse")
        assertEquals(CreateShopAccountResult.Rejected(CreateShopAccountError.SHOP_NOT_READY), result)
        assertTrue(accounts.saved.isEmpty())
    }
}
