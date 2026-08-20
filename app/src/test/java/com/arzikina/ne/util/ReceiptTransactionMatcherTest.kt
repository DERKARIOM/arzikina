package com.arzikina.ne.util

import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.AccountIcon
import com.arzikina.ne.domain.model.AccountType
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.CategoryIcon
import com.arzikina.ne.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Voir la doc de tête de [ReceiptTransactionMatcher]. */
class ReceiptTransactionMatcherTest {

    @Test
    fun `compte trouve par package Mobile Money exact`() {
        val airtel = account(id = 1L, mobileMoneyPackageName = "com.airtel.money")
        val orange = account(id = 2L, mobileMoneyPackageName = "com.orange.money")

        val result = ReceiptTransactionMatcher.matchAccountBySourceApp(
            accounts = listOf(airtel, orange),
            sourceApp = "com.orange.money"
        )

        assertEquals(orange, result)
    }

    @Test
    fun `aucun compte ne correspond au package source`() {
        val airtel = account(id = 1L, mobileMoneyPackageName = "com.airtel.money")

        val result = ReceiptTransactionMatcher.matchAccountBySourceApp(
            accounts = listOf(airtel),
            sourceApp = "com.wave.mobile"
        )

        assertNull(result)
    }

    @Test
    fun `package source nul ne retourne jamais un compte au hasard`() {
        val airtel = account(id = 1L, mobileMoneyPackageName = "com.airtel.money")

        val result = ReceiptTransactionMatcher.matchAccountBySourceApp(
            accounts = listOf(airtel),
            sourceApp = null
        )

        assertNull(result)
    }

    @Test
    fun `compte sans package Mobile Money configure jamais choisi arbitrairement`() {
        val cash = account(id = 1L, mobileMoneyPackageName = null)

        val result = ReceiptTransactionMatcher.matchAccountBySourceApp(
            accounts = listOf(cash),
            sourceApp = "com.airtel.money"
        )

        assertNull(result)
    }

    @Test
    fun `categorie trouvee quand son nom apparait dans le mot-cle - insensible aux accents`() {
        val salaire = category(id = 1L, name = "Salaire")
        val transport = category(id = 2L, name = "Transport")

        val result = ReceiptTransactionMatcher.matchCategoryByKeyword(
            categories = listOf(salaire, transport),
            keyword = "Reçu de virement SALAIRE mensuel"
        )

        assertEquals(salaire, result)
    }

    @Test
    fun `categorie trouvee quand le mot-cle est contenu dans un nom compose`() {
        val assuranceVie = category(id = 1L, name = "Assurance vie")

        val result = ReceiptTransactionMatcher.matchCategoryByKeyword(
            categories = listOf(assuranceVie),
            keyword = "assurance"
        )

        assertEquals(assuranceVie, result)
    }

    @Test
    fun `aucune categorie ne correspond au mot-cle`() {
        val transport = category(id = 1L, name = "Transport")

        val result = ReceiptTransactionMatcher.matchCategoryByKeyword(
            categories = listOf(transport),
            keyword = "Transfert vers Ari Aoua"
        )

        assertNull(result)
    }

    @Test
    fun `mot-cle nul ou vide ne retourne jamais une categorie au hasard`() {
        val transport = category(id = 1L, name = "Transport")

        assertNull(ReceiptTransactionMatcher.matchCategoryByKeyword(listOf(transport), null))
        assertNull(ReceiptTransactionMatcher.matchCategoryByKeyword(listOf(transport), "   "))
    }

    @Test
    fun `nom de categorie trop court jamais retenu meme s il apparait dans le mot-cle`() {
        val shortNameCategory = category(id = 1L, name = "IT")

        val result = ReceiptTransactionMatcher.matchCategoryByKeyword(
            categories = listOf(shortNameCategory),
            keyword = "Facture internet du mois"
        )

        assertNull(result)
    }

    private fun account(id: Long, mobileMoneyPackageName: String?) = Account(
        id = id,
        name = "Compte $id",
        icon = AccountIcon.MOBILE_MONEY,
        colorArgb = 0xFF000000,
        currencyCode = "XOF",
        initialBalance = 0L,
        createdAt = 0L,
        type = AccountType.MOBILE_MONEY,
        mobileMoneyPackageName = mobileMoneyPackageName
    )

    private fun category(id: Long, name: String) = Category(
        id = id,
        name = name,
        icon = CategoryIcon.OTHER,
        colorArgb = 0xFF000000,
        type = TransactionType.EXPENSE,
        createdAt = 0L
    )
}
