package com.arzikina.ne.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arzikina.ne.data.local.database.ArzikinaDatabase
import com.arzikina.ne.data.local.entity.AccountEntity
import com.arzikina.ne.data.local.entity.PersonEntity
import com.arzikina.ne.domain.model.Account
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Régression du bug critique corrigé à l'Étape "Tests et correction des bugs" : supprimer un
 * compte pouvait laisser des transactions orphelines (prêt/emprunt supprimé en cascade dont un
 * remboursement était réglé sur un AUTRE compte, resté intact) ou un prêt/emprunt avec un montant
 * remboursé périmé (remboursement supprimé en cascade sur CE compte, mais le prêt parent — sur un
 * compte différent — jamais recalculé). Voir la doc de `AccountRepositoryImpl.deleteAccount`.
 */
@RunWith(AndroidJUnit4::class)
class AccountRepositoryImplDeletionTest {

    private lateinit var database: ArzikinaDatabase
    private lateinit var accountRepository: AccountRepositoryImpl
    private lateinit var loanRepository: LoanRepositoryImpl
    private val userId = FakeSessionManager.DEFAULT_USER_ID
    private var personId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, ArzikinaDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val sessionManager = FakeSessionManager(userId)
        accountRepository = AccountRepositoryImpl(
            database = database,
            accountDao = database.accountDao(),
            cardSecretDao = database.cardSecretDao(),
            loanDao = database.loanDao(),
            loanPaymentDao = database.loanPaymentDao(),
            transactionDao = database.transactionDao(),
            sessionManager = sessionManager,
            ioDispatcher = Dispatchers.IO
        )
        loanRepository = LoanRepositoryImpl(
            database = database,
            loanDao = database.loanDao(),
            loanPaymentDao = database.loanPaymentDao(),
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            sessionManager = sessionManager,
            ioDispatcher = Dispatchers.IO
        )

        personId = database.personDao().upsert(
            PersonEntity(userId = userId, name = "Aïcha", phone = null, createdAt = 0L)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun createAccount(name: String): Long =
        accountRepository.saveAccount(
            Account(
                name = name,
                icon = AccountIcon.CASH,
                colorArgb = 0xFF000000,
                currencyCode = "XOF",
                initialBalance = 0L,
                createdAt = 0L
            )
        )

    private fun newLoan(accountId: Long, amount: Long = 10_000L) = Loan(
        personId = personId,
        accountId = accountId,
        type = LoanType.LENT,
        amount = amount,
        amountRepaid = 0L,
        remainingAmount = amount,
        startDate = System.currentTimeMillis() - 1_000L,
        dueDate = System.currentTimeMillis() + 1_000_000L,
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
    fun suppressionDuCompteDuPret_nettoieAussiLaTransactionDuVersementSurUnAutreCompte() = runBlocking {
        val loanAccountId = createAccount("Compte du prêt")
        val paymentAccountId = createAccount("Compte du remboursement")

        val loanId = loanRepository.saveLoan(newLoan(accountId = loanAccountId))
        loanRepository.recordPayment(
            LoanPayment(
                loanId = loanId,
                accountId = paymentAccountId, // remboursement sur un compte DIFFÉRENT du prêt
                amount = 3_000L,
                date = System.currentTimeMillis(),
                note = "",
                transactionId = 0L,
                createdAt = 0L
            )
        )
        val payment = database.loanPaymentDao().getAllForLoan(loanId, userId).single()

        accountRepository.deleteAccount(loanAccountId)

        // Le prêt disparaît (cascade sur son propre compte), et la transaction du remboursement —
        // pourtant sur le compte SURVIVANT — ne doit plus traîner sans plus aucun prêt/versement
        // qui la référence.
        assertNull(loanRepository.getLoan(loanId))
        assertNull(database.transactionDao().getById(payment.transactionId, userId))
        // Le compte de règlement, lui, doit être intact (seul le compte du prêt a été supprimé).
        assertTrue(database.accountDao().getById(paymentAccountId, userId) != null)
    }

    @Test
    fun suppressionDuCompteDeReglement_recalculeLePretParentSansLeSupprimer() = runBlocking {
        val loanAccountId = createAccount("Compte du prêt")
        val paymentAccountId = createAccount("Compte du remboursement")

        val loanId = loanRepository.saveLoan(newLoan(accountId = loanAccountId, amount = 10_000L))
        loanRepository.recordPayment(
            LoanPayment(
                loanId = loanId,
                accountId = paymentAccountId,
                amount = 4_000L,
                date = System.currentTimeMillis(),
                note = "",
                transactionId = 0L,
                createdAt = 0L
            )
        )

        accountRepository.deleteAccount(paymentAccountId)

        // Le prêt lui-même survit (son propre compte n'a pas été touché), mais le versement
        // disparu en cascade doit être répercuté sur son montant remboursé/solde/statut — sinon le
        // prêt resterait figé à "4 000 déjà remboursés" pour un versement qui n'existe plus.
        val loanAfter = loanRepository.getLoan(loanId)!!
        assertEquals(0L, loanAfter.amountRepaid)
        assertEquals(10_000L, loanAfter.remainingAmount)
        assertEquals(LoanStatus.ONGOING, loanAfter.status)
        assertTrue(database.loanPaymentDao().getAllForLoan(loanId, userId).isEmpty())
    }
}
