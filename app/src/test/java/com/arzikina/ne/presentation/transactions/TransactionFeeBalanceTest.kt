package com.arzikina.ne.presentation.transactions

import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.AccountIcon
import com.arzikina.ne.domain.model.FeeType
import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.presentation.accounts.computeCurrentBalances
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Un "frais" (voir cahier des charges "Gestion des frais supplémentaires sur les transactions")
 * n'est PAS un champ numérique sur [Transaction] — c'est une DEUXIÈME [Transaction] normale
 * (`type = EXPENSE`, catégorie système "Frais et commissions") auto-générée et liée via
 * [Transaction.feeTransactionId] sur la transaction principale (voir `TransactionRepositoryImpl`).
 *
 * Ces tests ne couvrent donc volontairement AUCUNE logique de frais dédiée dans
 * [computeCurrentBalances]/[computeRunningBalances] — ces deux fonctions n'ont pas été modifiées
 * pour cette fonctionnalité et n'ont besoin de rien connaître des frais. Leur seul but est de
 * VERROUILLER cet invariant architectural : représenter un frais comme un second mouvement normal
 * suffit, dans TOUS les cas du cahier des charges (dépense, revenu, transfert ; compte des frais
 * = compte source ou compte tiers), à produire le bon solde sur chaque compte concerné, sans
 * aucune addition/soustraction manuelle de `amount` et `fee`. Une régression future qui
 * réintroduirait un traitement spécial des frais dans ces fonctions casserait ces tests.
 */
class TransactionFeeBalanceTest {

    private fun account(id: Long, initialBalance: Long = 0L) = Account(
        id = id,
        name = "Compte $id",
        icon = AccountIcon.CASH,
        colorArgb = 0xFF000000L,
        currencyCode = "XOF",
        initialBalance = initialBalance,
        createdAt = 0L
    )

    /** Transaction "principale", éventuellement liée à une transaction de frais via [feeTransactionId]. */
    private fun transaction(
        id: Long,
        type: TransactionType,
        accountId: Long,
        amount: Long,
        transferAccountId: Long? = null,
        feeTransactionId: Long? = null
    ) = Transaction(
        id = id,
        amount = amount,
        type = type,
        accountId = accountId,
        transferAccountId = transferAccountId,
        categoryId = if (type == TransactionType.TRANSFER) null else 1L,
        date = 0L,
        createdAt = 0L,
        feeTransactionId = feeTransactionId
    )

    /** Transaction de frais auto-générée (voir doc de tête) : toujours EXPENSE, catégorie frais. */
    private fun feeTransaction(id: Long, accountId: Long, amount: Long) = Transaction(
        id = id,
        amount = amount,
        type = TransactionType.EXPENSE,
        accountId = accountId,
        categoryId = FEES_CATEGORY_ID,
        date = 0L,
        createdAt = 0L,
        feeType = FeeType.BANK
    )

    // --- Dépense avec frais ------------------------------------------------------------------

    @Test
    fun `depense avec frais, compte des frais egal au compte source - debite du total`() {
        val account = account(id = 1, initialBalance = 100_000L)
        val expense = transaction(1, TransactionType.EXPENSE, accountId = 1, amount = 50_000L, feeTransactionId = 2)
        val fee = feeTransaction(2, accountId = 1, amount = 500L)

        val balances = computeCurrentBalances(listOf(account), listOf(expense, fee))

        // 100 000 - 50 000 - 500 = 49 500, jamais 100 000 - 50 500 calculé "à la main".
        assertEquals(49_500L, balances[1])
    }

    @Test
    fun `depense avec frais, compte des frais tiers - chaque compte reflete son propre mouvement`() {
        val source = account(id = 1, initialBalance = 100_000L)
        val feeAccount = account(id = 2, initialBalance = 10_000L)
        val expense = transaction(1, TransactionType.EXPENSE, accountId = 1, amount = 50_000L, feeTransactionId = 2)
        val fee = feeTransaction(2, accountId = 2, amount = 500L)

        val balances = computeCurrentBalances(listOf(source, feeAccount), listOf(expense, fee))

        assertEquals(50_000L, balances[1]) // 100 000 - 50 000, AUCUN frais ici.
        assertEquals(9_500L, balances[2]) // 10 000 - 500, sur le compte tiers uniquement.
    }

    // --- Revenu avec frais ---------------------------------------------------------------------

    @Test
    fun `revenu avec frais, compte des frais egal au compte credite - revenu net recu`() {
        val account = account(id = 1, initialBalance = 0L)
        val income = transaction(1, TransactionType.INCOME, accountId = 1, amount = 10_000L, feeTransactionId = 2)
        val fee = feeTransaction(2, accountId = 1, amount = 500L)

        val balances = computeCurrentBalances(listOf(account), listOf(income, fee))

        // "Revenu net" (voir cahier des charges) obtenu par deux mouvements distincts (+10 000, -500),
        // jamais par un calcul explicite "amount - fee".
        assertEquals(9_500L, balances[1])
    }

    @Test
    fun `revenu avec frais, compte des frais tiers - le compte credite recoit le montant brut`() {
        val incomeAccount = account(id = 1, initialBalance = 0L)
        val feeAccount = account(id = 2, initialBalance = 5_000L)
        val income = transaction(1, TransactionType.INCOME, accountId = 1, amount = 10_000L, feeTransactionId = 2)
        val fee = feeTransaction(2, accountId = 2, amount = 500L)

        val balances = computeCurrentBalances(listOf(incomeAccount, feeAccount), listOf(income, fee))

        assertEquals(10_000L, balances[1]) // Montant BRUT, le frais ne le touche pas.
        assertEquals(4_500L, balances[2]) // Débité séparément sur le compte tiers.
    }

    // --- Transfert avec frais (cas emblématique du cahier des charges) -------------------------

    @Test
    fun `transfert avec frais, compte des frais egal au compte source - destination recoit exactement le montant`() {
        val source = account(id = 1, initialBalance = 100_000L)
        val destination = account(id = 2, initialBalance = 0L)
        // Exemple du cahier des charges : virement de 50 000 avec 500 de frais.
        val transfer = transaction(1, TransactionType.TRANSFER, accountId = 1, amount = 50_000L, transferAccountId = 2, feeTransactionId = 3)
        val fee = feeTransaction(3, accountId = 1, amount = 500L)

        val balances = computeCurrentBalances(listOf(source, destination), listOf(transfer, fee))

        assertEquals(49_500L, balances[1]) // 100 000 - 50 000 - 500 : source débitée du total.
        assertEquals(50_000L, balances[2]) // Destination reçoit EXACTEMENT le montant, jamais le frais.
    }

    @Test
    fun `transfert avec frais, compte des frais tiers - source et destination ne portent que le montant`() {
        val source = account(id = 1, initialBalance = 100_000L)
        val destination = account(id = 2, initialBalance = 0L)
        val feeAccount = account(id = 3, initialBalance = 20_000L)
        val transfer = transaction(1, TransactionType.TRANSFER, accountId = 1, amount = 50_000L, transferAccountId = 2, feeTransactionId = 4)
        val fee = feeTransaction(4, accountId = 3, amount = 500L)

        val balances = computeCurrentBalances(listOf(source, destination, feeAccount), listOf(transfer, fee))

        assertEquals(50_000L, balances[1]) // Source débitée du seul montant du virement.
        assertEquals(50_000L, balances[2]) // Destination inchangée par rapport au cas précédent.
        assertEquals(19_500L, balances[3]) // Frais entièrement porté par le compte tiers.
    }

    // --- Non-régression : aucun frais -----------------------------------------------------------

    @Test
    fun `transaction sans frais - comportement inchange, feeTransactionId null`() {
        val account = account(id = 1, initialBalance = 10_000L)
        val expense = transaction(1, TransactionType.EXPENSE, accountId = 1, amount = 3_000L)

        val balances = computeCurrentBalances(listOf(account), listOf(expense))

        assertEquals(7_000L, balances[1])
    }

    // --- computeRunningBalances : même invariant sur le solde APRÈS PASSAGE de chaque ligne ------

    @Test
    fun `solde apres passage - la ligne de frais a son propre solde, distinct de la transaction principale`() {
        val account = account(id = 1, initialBalance = 100_000L)
        // Ordre plus récent -> plus ancien (voir doc de computeRunningBalances, position 0 = le
        // plus récent) : la ligne de frais est listée EN PREMIER, comme le ferait
        // `observeTransactions` (ORDER BY date DESC) pour deux lignes à la même date où la ligne de
        // frais serait la plus récemment écrite en base.
        val expense = transaction(1, TransactionType.EXPENSE, accountId = 1, amount = 50_000L, feeTransactionId = 2)
        val fee = feeTransaction(2, accountId = 1, amount = 500L)

        val runningBalances = computeRunningBalances(listOf(fee, expense), listOf(account))

        // Solde du compte juste après la ligne de frais (la plus récente ici) = solde courant final :
        // 100 000 - 50 000 - 500 = 49 500.
        assertEquals(49_500L, runningBalances[2L to 1L])
        // Solde du compte juste après la dépense SEULE (avant que sa ligne de frais ne soit
        // décomptée) : 100 000 - 50 000 = 50 000, distinct du solde final ci-dessus.
        assertEquals(50_000L, runningBalances[1L to 1L])
    }

    private companion object {
        const val FEES_CATEGORY_ID = 99L
    }
}
