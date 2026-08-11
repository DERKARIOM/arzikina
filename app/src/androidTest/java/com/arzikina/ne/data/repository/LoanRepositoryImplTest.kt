package com.arzikina.ne.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arzikina.ne.data.local.database.ArzikinaDatabase
import com.arzikina.ne.data.local.entity.AccountEntity
import com.arzikina.ne.data.local.entity.PersonEntity
import com.arzikina.ne.domain.model.AccountIcon
import com.arzikina.ne.domain.model.Loan
import com.arzikina.ne.domain.model.LoanPayment
import com.arzikina.ne.domain.model.LoanReason
import com.arzikina.ne.domain.model.LoanStatus
import com.arzikina.ne.domain.model.LoanType
import com.arzikina.ne.domain.model.RepaymentMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test d'intégration Room EN MÉMOIRE (voir `gradle/libs.versions.toml`, section "Tests
 * instrumentés") pour [LoanRepositoryImpl] : contrairement aux ViewModels (testés en JVM pur avec
 * de faux repositories), ce repository EST la couche qui parle réellement à SQLite — le tester
 * avec de vrais DAO Room est donc la seule façon de vérifier ses écritures atomiques
 * ([androidx.room.RoomDatabase.withTransaction]) et la synchronisation prêt/emprunt <-> transaction
 * (voir la doc de [com.arzikina.ne.domain.repository.LoanRepository]).
 */
@RunWith(AndroidJUnit4::class)
class LoanRepositoryImplTest {

    private lateinit var database: ArzikinaDatabase
    private lateinit var repository: LoanRepositoryImpl
    private val userId = FakeSessionManager.DEFAULT_USER_ID
    private var accountId: Long = 0L
    private var personId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, ArzikinaDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = LoanRepositoryImpl(
            database = database,
            loanDao = database.loanDao(),
            loanPaymentDao = database.loanPaymentDao(),
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            sessionManager = FakeSessionManager(userId),
            ioDispatcher = Dispatchers.IO
        )

        // Un prêt/emprunt a TOUJOURS une personne et un compte (voir la doc de `Loan`) : les
        // contraintes de clé étrangère de `loans`/`loan_payments` l'exigent aussi côté SQLite.
        accountId = database.accountDao().upsert(
            AccountEntity(
                userId = userId,
                name = "Espèces",
                icon = AccountIcon.CASH,
                colorArgb = 0xFF000000,
                currencyCode = "XOF",
                initialBalanceMinor = 0L,
                createdAt = 0L
            )
        )
        personId = database.personDao().upsert(
            PersonEntity(userId = userId, name = "Aïcha", phone = null, createdAt = 0L)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun newLoan(amount: Long = 10_000L, dueDate: Long = System.currentTimeMillis() + 1_000_000L) = Loan(
        personId = personId,
        accountId = accountId,
        type = LoanType.LENT,
        amount = amount,
        amountRepaid = 0L,
        remainingAmount = amount,
        startDate = System.currentTimeMillis() - 1_000L,
        dueDate = dueDate,
        reason = LoanReason.OTHER,
        reasonCustomText = null,
        repaymentMode = RepaymentMode.SINGLE,
        description = "Test",
        status = LoanStatus.ONGOING,
        createdAt = 0L,
        updatedAt = 0L,
        transactionId = 0L
    )

    @Test
    fun saveLoan_creeLePretEtSaTransactionDeDecaissement() = runBlocking {
        val loanId = repository.saveLoan(newLoan(amount = 10_000L))

        val saved = repository.getLoan(loanId)
        assertNotNull(saved)
        assertEquals(0L, saved!!.amountRepaid)
        assertEquals(10_000L, saved.remainingAmount)
        assertEquals(LoanStatus.ONGOING, saved.status)
        assertTrue(saved.transactionId != 0L)

        val transaction = database.transactionDao().getById(saved.transactionId, userId)
        assertNotNull(transaction)
        assertEquals(10_000L, transaction!!.amount)
        assertEquals(accountId, transaction.accountId)
    }

    @Test
    fun recordPayment_metAJourLeMontantRembourseEtLeStatut() = runBlocking {
        val loanId = repository.saveLoan(newLoan(amount = 10_000L))

        repository.recordPayment(
            LoanPayment(
                loanId = loanId,
                accountId = accountId,
                amount = 4_000L,
                date = System.currentTimeMillis(),
                note = "",
                transactionId = 0L,
                createdAt = 0L
            )
        )

        val afterFirstPayment = repository.getLoan(loanId)!!
        assertEquals(4_000L, afterFirstPayment.amountRepaid)
        assertEquals(6_000L, afterFirstPayment.remainingAmount)
        assertEquals(LoanStatus.ONGOING, afterFirstPayment.status)

        repository.recordPayment(
            LoanPayment(
                loanId = loanId,
                accountId = accountId,
                amount = 6_000L,
                date = System.currentTimeMillis(),
                note = "Solde",
                transactionId = 0L,
                createdAt = 0L
            )
        )

        val afterFullRepayment = repository.getLoan(loanId)!!
        assertEquals(10_000L, afterFullRepayment.amountRepaid)
        assertEquals(0L, afterFullRepayment.remainingAmount)
        assertEquals(LoanStatus.REPAID, afterFullRepayment.status)
    }

    @Test
    fun recordPayment_refuseUnMontantSuperieurAuSoldeRestant() = runBlocking {
        val loanId = repository.saveLoan(newLoan(amount = 10_000L))

        var threw = false
        try {
            repository.recordPayment(
                LoanPayment(
                    loanId = loanId,
                    accountId = accountId,
                    amount = 10_001L, // dépasse le solde restant (10 000)
                    date = System.currentTimeMillis(),
                    note = "",
                    transactionId = 0L,
                    createdAt = 0L
                )
            )
        } catch (e: IllegalStateException) {
            threw = true
        }

        assertTrue("recordPayment aurait dû lever IllegalStateException", threw)
        // Aucun effet de bord : le prêt/emprunt reste inchangé après le refus.
        assertEquals(0L, repository.getLoan(loanId)!!.amountRepaid)
    }

    @Test
    fun deleteLoan_supprimeLePretSesVersementsEtToutesLeursTransactions() = runBlocking {
        val loanId = repository.saveLoan(newLoan(amount = 10_000L))
        val loanBefore = repository.getLoan(loanId)!!
        repository.recordPayment(
            LoanPayment(
                loanId = loanId,
                accountId = accountId,
                amount = 3_000L,
                date = System.currentTimeMillis(),
                note = "",
                transactionId = 0L,
                createdAt = 0L
            )
        )
        val payment = database.loanPaymentDao().getAllForLoan(loanId, userId).single()

        repository.deleteLoan(loanId)

        assertNull(repository.getLoan(loanId))
        assertTrue(database.loanPaymentDao().getAllForLoan(loanId, userId).isEmpty())
        assertNull(database.transactionDao().getById(loanBefore.transactionId, userId))
        assertNull(database.transactionDao().getById(payment.transactionId, userId))
    }

    @Test
    fun deletePayment_recalculeLePretEtSupprimeLaTransactionLiee() = runBlocking {
        val loanId = repository.saveLoan(newLoan(amount = 10_000L))
        repository.recordPayment(
            LoanPayment(
                loanId = loanId,
                accountId = accountId,
                amount = 4_000L,
                date = System.currentTimeMillis(),
                note = "",
                transactionId = 0L,
                createdAt = 0L
            )
        )
        val payment = database.loanPaymentDao().getAllForLoan(loanId, userId).single()

        repository.deletePayment(payment.id)

        val loanAfter = repository.getLoan(loanId)!!
        assertEquals(0L, loanAfter.amountRepaid)
        assertEquals(10_000L, loanAfter.remainingAmount)
        assertEquals(LoanStatus.ONGOING, loanAfter.status)
        assertNull(database.transactionDao().getById(payment.transactionId, userId))
        assertTrue(database.loanPaymentDao().getAllForLoan(loanId, userId).isEmpty())
    }

    @Test
    fun findLoanIdForTransaction_retrouveLePretViaSonDecaissementOuUnRemboursement() = runBlocking {
        val loanId = repository.saveLoan(newLoan(amount = 10_000L))
        val loan = repository.getLoan(loanId)!!
        repository.recordPayment(
            LoanPayment(
                loanId = loanId,
                accountId = accountId,
                amount = 1_000L,
                date = System.currentTimeMillis(),
                note = "",
                transactionId = 0L,
                createdAt = 0L
            )
        )
        val payment = database.loanPaymentDao().getAllForLoan(loanId, userId).single()

        assertEquals(loanId, repository.findLoanIdForTransaction(loan.transactionId))
        assertEquals(loanId, repository.findLoanIdForTransaction(payment.transactionId))
        // Une transaction normale, sans rapport avec un prêt/emprunt, ne doit rien retourner.
        val normalTransactionId = database.transactionDao().upsert(
            com.arzikina.ne.data.local.entity.TransactionEntity(
                userId = userId,
                amount = 500L,
                type = com.arzikina.ne.domain.model.TransactionType.EXPENSE,
                accountId = accountId,
                transferAccountId = null,
                categoryId = null,
                date = System.currentTimeMillis(),
                description = "Achat quelconque",
                receiptPhotoUri = null,
                latitude = null,
                longitude = null,
                paymentMethod = null,
                createdAt = 0L
            )
        )
        assertNull(repository.findLoanIdForTransaction(normalTransactionId))
    }
}
