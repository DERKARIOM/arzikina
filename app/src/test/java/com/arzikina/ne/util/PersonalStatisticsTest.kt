package com.arzikina.ne.util

import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.AccountIcon
import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [PersonalStatistics.scope] est une fonction pure : idéale pour des tests unitaires
 * exhaustifs sans aucun mock. Couvre les cas limites du cahier des charges "Exclure un
 * compte des statistiques" (comptes exclus retirés du périmètre, leurs transactions
 * retirées, transferts entre compte inclus/exclu jamais comptés en revenu/dépense).
 */
class PersonalStatisticsTest {

    private fun account(id: Long, excluded: Boolean = false) = Account(
        id = id,
        name = "Compte $id",
        icon = AccountIcon.CASH,
        colorArgb = 0xFF000000L,
        currencyCode = "XOF",
        initialBalance = 0L,
        createdAt = 0L,
        isExcludedFromStatistics = excluded
    )

    private fun transaction(
        id: Long,
        type: TransactionType,
        accountId: Long,
        transferAccountId: Long? = null,
        amount: Long = 1_000L
    ) = Transaction(
        id = id,
        amount = amount,
        type = type,
        accountId = accountId,
        transferAccountId = transferAccountId,
        categoryId = if (type == TransactionType.TRANSFER) null else 1L,
        date = 0L,
        createdAt = 0L
    )

    @Test
    fun `aucun compte exclu - le perimetre contient tout, inchange`() {
        val accounts = listOf(account(1), account(2))
        val transactions = listOf(
            transaction(1, TransactionType.EXPENSE, accountId = 1),
            transaction(2, TransactionType.INCOME, accountId = 2)
        )

        val scope = PersonalStatistics.scope(accounts, transactions)

        assertEquals(accounts, scope.accounts)
        assertEquals(transactions, scope.transactions)
    }

    @Test
    fun `un compte exclu est retire du perimetre`() {
        val included = account(1)
        val excluded = account(2, excluded = true)

        val scope = PersonalStatistics.scope(listOf(included, excluded), transactions = emptyList())

        assertEquals(listOf(included), scope.accounts)
    }

    @Test
    fun `les transactions du compte exclu sont retirees, celles du compte inclus restent`() {
        val included = account(1)
        val excluded = account(2, excluded = true)
        val ownTransaction = transaction(1, TransactionType.EXPENSE, accountId = 1)
        val excludedTransaction = transaction(2, TransactionType.EXPENSE, accountId = 2)

        val scope = PersonalStatistics.scope(listOf(included, excluded), listOf(ownTransaction, excludedTransaction))

        assertEquals(listOf(ownTransaction), scope.transactions)
    }

    @Test
    fun `tous les comptes exclus - perimetre entierement vide`() {
        val accounts = listOf(account(1, excluded = true), account(2, excluded = true))
        val transactions = listOf(
            transaction(1, TransactionType.EXPENSE, accountId = 1),
            transaction(2, TransactionType.INCOME, accountId = 2)
        )

        val scope = PersonalStatistics.scope(accounts, transactions)

        assertEquals(emptyList<Account>(), scope.accounts)
        assertEquals(emptyList<Transaction>(), scope.transactions)
    }

    @Test
    fun `transfert d'un compte inclus vers un compte exclu reste dans le perimetre (compte source inclus)`() {
        val included = account(1)
        val excluded = account(2, excluded = true)
        val transfer = transaction(1, TransactionType.TRANSFER, accountId = 1, transferAccountId = 2)

        val scope = PersonalStatistics.scope(listOf(included, excluded), listOf(transfer))

        assertEquals(listOf(transfer), scope.transactions)
    }

    @Test
    fun `transfert d'un compte exclu vers un compte inclus est retire (compte source exclu)`() {
        val included = account(1)
        val excluded = account(2, excluded = true)
        val transfer = transaction(1, TransactionType.TRANSFER, accountId = 2, transferAccountId = 1)

        val scope = PersonalStatistics.scope(listOf(included, excluded), listOf(transfer))

        assertEquals(emptyList<Transaction>(), scope.transactions)
    }

    @Test
    fun `une depense ou un revenu sur le compte exclu n'entre jamais dans le perimetre`() {
        val included = account(1)
        val excluded = account(2, excluded = true)
        val expenseOnExcluded = transaction(1, TransactionType.EXPENSE, accountId = 2)
        val incomeOnExcluded = transaction(2, TransactionType.INCOME, accountId = 2)
        val expenseOnIncluded = transaction(3, TransactionType.EXPENSE, accountId = 1)

        val scope = PersonalStatistics.scope(
            listOf(included, excluded),
            listOf(expenseOnExcluded, incomeOnExcluded, expenseOnIncluded)
        )

        assertEquals(listOf(expenseOnIncluded), scope.transactions)
    }

    @Test
    fun `liste de comptes et de transactions vides - perimetre vide sans erreur`() {
        val scope = PersonalStatistics.scope(emptyList(), emptyList())

        assertEquals(emptyList<Account>(), scope.accounts)
        assertEquals(emptyList<Transaction>(), scope.transactions)
    }
}
