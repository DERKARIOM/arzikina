package com.naniger.arzikina.data.repository

import com.naniger.arzikina.data.local.dao.CategoryDao
import com.naniger.arzikina.data.local.dao.LoanDao
import com.naniger.arzikina.data.local.dao.TransactionDao
import com.naniger.arzikina.data.local.entity.CategoryEntity
import com.naniger.arzikina.data.local.entity.LoanEntity
import com.naniger.arzikina.data.local.entity.TransactionEntity
import com.naniger.arzikina.data.mapper.toEntity
import com.naniger.arzikina.domain.model.CategoryIcon
import com.naniger.arzikina.domain.model.Loan
import com.naniger.arzikina.domain.model.LoanGiftException
import com.naniger.arzikina.domain.model.LoanReason
import com.naniger.arzikina.domain.model.LoanStatus
import com.naniger.arzikina.domain.model.LoanType
import com.naniger.arzikina.domain.model.RepaymentMode
import com.naniger.arzikina.domain.model.SyncOperation
import com.naniger.arzikina.domain.model.Transaction
import com.naniger.arzikina.domain.model.TransactionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * « Transformer en cadeau » côté data (voir [LoanGiftConverter]) : écritures réelles, conservation
 * de la personne/date/compte, et surtout INVARIANT FINANCIER — la somme signée des transactions
 * du prêt/emprunt est identique avant et après (aucune double écriture, solde inchangé).
 */
class LoanGiftConverterTest {

    private val userId = 1L
    private val day = 24L * 60 * 60 * 1000
    private val start = 1_780_000_000_000L
    private val now = start + 10 * day

    private val loanDao = mockk<LoanDao>(relaxed = true)
    private val transactionDao = mockk<TransactionDao>(relaxed = true)
    private val categoryDao = mockk<CategoryDao>(relaxed = true)
    private val categorySyncEnqueuer = mockk<CategorySyncEnqueuer>(relaxed = true)
    private val converter = LoanGiftConverter(loanDao, transactionDao, categoryDao, categorySyncEnqueuer)

    private val giftsExpense = category(id = 50L, type = TransactionType.EXPENSE)
    private val giftsIncome = category(id = 51L, type = TransactionType.INCOME)

    /** Toutes les écritures de transactions, dans l'ordre. */
    private val upsertedTransactions = mutableListOf<TransactionEntity>()
    private var upsertedLoan: LoanEntity? = null

    private fun category(id: Long, type: TransactionType) = CategoryEntity(
        id = id, userId = userId, name = "Cadeaux", icon = CategoryIcon.GIFTS,
        colorArgb = 0xFFEC4899L, type = type, createdAt = start, syncId = "cat-$id"
    )

    private fun loanEntity(type: LoanType, amount: Long = 100_000L, repaid: Long = 0L, gifted: Long = 0L) = Loan(
        id = 7L, personId = 3L, accountId = 2L, type = type,
        amount = amount, amountRepaid = repaid, remainingAmount = amount - repaid - gifted,
        startDate = start, dueDate = start + 30 * day, reason = LoanReason.OTHER,
        repaymentMode = RepaymentMode.SINGLE, status = LoanStatus.ONGOING,
        createdAt = start, updatedAt = start, transactionId = 10L,
        giftedAmount = gifted, giftTransactionId = if (gifted > 0) 10L else null
    ).toEntity(userId).copy(syncId = "loan-sync")

    private fun disbursementFor(loan: LoanEntity) = Transaction(
        id = loan.transactionId,
        amount = loan.amount,
        type = if (loan.type == LoanType.LENT) TransactionType.EXPENSE else TransactionType.INCOME,
        accountId = loan.accountId,
        categoryId = 20L,
        date = loan.startDate,
        description = "Prêt accordé",
        createdAt = start
    ).toEntity(userId).copy(syncId = "disb-sync", updatedAt = start)

    private fun given(loan: LoanEntity): TransactionEntity {
        val disbursement = disbursementFor(loan)
        coEvery { loanDao.getById(loan.id, userId) } returns loan
        coEvery { transactionDao.getById(loan.transactionId, userId) } returns disbursement
        coEvery { categoryDao.getFirstByNameAndTypeForUser("Cadeaux", TransactionType.EXPENSE, userId) } returns giftsExpense
        coEvery { categoryDao.getFirstByNameAndTypeForUser("Cadeaux", TransactionType.INCOME, userId) } returns giftsIncome
        coEvery { transactionDao.upsert(any()) } answers {
            val entity = firstArg<TransactionEntity>()
            upsertedTransactions += entity
            if (entity.id == 0L) 99L else -1L
        }
        coEvery { loanDao.upsert(any()) } answers { upsertedLoan = firstArg(); -1L }
        return disbursement
    }

    /** Effet net sur le solde du compte du prêt : décaissement (+ cadeau) signés. */
    private fun signed(t: TransactionEntity) = if (t.type == TransactionType.INCOME) t.amount else -t.amount

    @Test
    fun `pret non rembourse - decaissement reclasse sur place, aucune nouvelle ligne`() = runTest {
        val loan = loanEntity(LoanType.LENT)
        val disbursement = given(loan)

        val result = converter.convert(loan.id, "Cadeau à Abdou", userId, now)

        assertEquals(1, upsertedTransactions.size)
        val reclassified = upsertedTransactions.single()
        assertEquals(disbursement.id, reclassified.id)
        assertEquals("disb-sync", reclassified.syncId) // même ligne côté serveur : pas de doublon
        assertEquals(100_000L, reclassified.amount)
        assertEquals(TransactionType.EXPENSE, reclassified.type)
        assertEquals(giftsExpense.id, reclassified.categoryId)
        assertEquals("Cadeau à Abdou", reclassified.description)
        assertEquals(disbursement.date, reclassified.date)
        assertEquals(disbursement.accountId, reclassified.accountId)
        assertEquals(signed(disbursement), signed(reclassified))

        assertEquals(disbursement.id, result.giftTransactionId)
        assertEquals(listOf(SyncOperation.UPDATE), result.transactionOps.map { it.second })
    }

    @Test
    fun `emprunt non rembourse - reste un revenu, categorie Cadeaux revenu`() = runTest {
        val loan = loanEntity(LoanType.BORROWED)
        given(loan)

        converter.convert(loan.id, "Cadeau de la part d'Aïcha", userId, now)

        val reclassified = upsertedTransactions.single()
        assertEquals(TransactionType.INCOME, reclassified.type)
        assertEquals(giftsIncome.id, reclassified.categoryId)
        assertEquals("Cadeau de la part d'Aïcha", reclassified.description)
    }

    @Test
    fun `pret partiellement rembourse - decaissement reduit et cadeau du reste, solde inchange`() = runTest {
        val loan = loanEntity(LoanType.LENT, repaid = 40_000L)
        val disbursement = given(loan)

        val result = converter.convert(loan.id, "Cadeau à Abdou", userId, now)

        assertEquals(2, upsertedTransactions.size)
        val (reduced, gift) = upsertedTransactions
        assertEquals(disbursement.id, reduced.id)
        assertEquals(40_000L, reduced.amount)
        assertEquals(20L, reduced.categoryId) // le décaissement garde sa catégorie « Prêt accordé »

        assertEquals(0L, gift.id)
        assertEquals(60_000L, gift.amount)
        assertEquals(TransactionType.EXPENSE, gift.type)
        assertEquals(giftsExpense.id, gift.categoryId)
        assertEquals(disbursement.accountId, gift.accountId)
        assertEquals(disbursement.date, gift.date)
        assertEquals("Cadeau à Abdou", gift.description)
        assertNotEquals(disbursement.syncId, gift.syncId)

        // INVARIANT : aucune double écriture, le solde ne bouge pas.
        assertEquals(signed(disbursement), signed(reduced) + signed(gift))

        assertEquals(99L, result.giftTransactionId)
        assertEquals(listOf(SyncOperation.UPDATE, SyncOperation.CREATE), result.transactionOps.map { it.second })
        assertEquals(99L, result.transactionOps[1].first.id)
    }

    @Test
    fun `emprunt partiellement rembourse - cadeau recu du reste, solde inchange`() = runTest {
        val loan = loanEntity(LoanType.BORROWED, repaid = 40_000L)
        val disbursement = given(loan)

        converter.convert(loan.id, "Cadeau de la part d'Aïcha", userId, now)

        val (reduced, gift) = upsertedTransactions
        assertEquals(40_000L, reduced.amount)
        assertEquals(60_000L, gift.amount)
        assertEquals(TransactionType.INCOME, gift.type)
        assertEquals(giftsIncome.id, gift.categoryId)
        assertEquals(signed(disbursement), signed(reduced) + signed(gift))
    }

    @Test
    fun `pret cloture - statut GIFTED, reste 0, montant et personne d origine conserves`() = runTest {
        val loan = loanEntity(LoanType.LENT, repaid = 40_000L)
        given(loan)

        val result = converter.convert(loan.id, "Cadeau à Abdou", userId, now)

        val gifted = requireNotNull(upsertedLoan)
        assertEquals(result.giftedLoan, gifted)
        assertEquals(LoanStatus.GIFTED, gifted.status)
        assertEquals(60_000L, gifted.giftedAmount)
        assertEquals(0L, gifted.remainingAmount)
        assertEquals(99L, gifted.giftTransactionId)
        assertEquals(now, gifted.giftedAt)
        // Traçabilité : rien d'historique n'est effacé.
        assertEquals(100_000L, gifted.amount)
        assertEquals(40_000L, gifted.amountRepaid)
        assertEquals(loan.personId, gifted.personId)
        assertEquals(loan.startDate, gifted.startDate)
        assertEquals(loan.transactionId, gifted.transactionId)
        assertEquals("loan-sync", gifted.syncId)
        assertNull(gifted.deletedAt)
    }

    @Test
    fun `pret entierement rembourse - refuse sans aucune ecriture`() = runTest {
        val loan = loanEntity(LoanType.LENT, repaid = 100_000L)
        given(loan)

        assertThrows(LoanGiftException.NotConvertible::class.java) {
            kotlinx.coroutines.runBlocking { converter.convert(loan.id, "x", userId, now) }
        }
        coVerify(exactly = 0) { transactionDao.upsert(any()) }
        coVerify(exactly = 0) { loanDao.upsert(any()) }
    }

    @Test
    fun `deja transforme - refuse, pas de second cadeau`() = runTest {
        val loan = loanEntity(LoanType.LENT, repaid = 40_000L, gifted = 60_000L)
        given(loan)

        assertThrows(LoanGiftException.NotConvertible::class.java) {
            kotlinx.coroutines.runBlocking { converter.convert(loan.id, "x", userId, now) }
        }
        coVerify(exactly = 0) { transactionDao.upsert(any()) }
    }

    @Test
    fun `pret introuvable - refuse`() = runTest {
        coEvery { loanDao.getById(any(), any()) } returns null
        assertThrows(LoanGiftException.NotConvertible::class.java) {
            kotlinx.coroutines.runBlocking { converter.convert(404L, "x", userId, now) }
        }
    }

    @Test
    fun `categorie Cadeaux revenu supprimee - recreee et synchronisee`() = runTest {
        val loan = loanEntity(LoanType.BORROWED)
        given(loan)
        var created: CategoryEntity? = null
        coEvery { categoryDao.getFirstByNameAndTypeForUser("Cadeaux", TransactionType.INCOME, userId) } answers { created }
        coEvery { categoryDao.upsert(any()) } answers { created = firstArg<CategoryEntity>().copy(id = 77L) }

        converter.convert(loan.id, "Cadeau de la part d'Aïcha", userId, now)

        assertEquals(TransactionType.INCOME, created?.type)
        assertEquals(77L, upsertedTransactions.single().categoryId)
        coVerify { categorySyncEnqueuer.enqueue(match { it.id == 77L }, SyncOperation.CREATE) }
    }
}
