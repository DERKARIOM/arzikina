package com.arzikina.ne.presentation.utilities.loans

import app.cash.turbine.test
import com.arzikina.ne.MainDispatcherRule
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.AccountIcon
import com.arzikina.ne.domain.model.Loan
import com.arzikina.ne.domain.model.LoanReason
import com.arzikina.ne.domain.model.LoanStatus
import com.arzikina.ne.domain.model.LoanType
import com.arzikina.ne.domain.model.Person
import com.arzikina.ne.domain.model.RepaymentMode
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.LoanRepository
import com.arzikina.ne.domain.repository.PersonRepository
import com.arzikina.ne.util.AppResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Vérifie les agrégations de [LoanStatisticsViewModel] : regroupement par devise (jamais de somme
 * across-devises, voir la doc de [com.arzikina.ne.domain.model.CurrencyAmount]) et signe du solde
 * net par personne (positif = elle doit de l'argent à l'utilisateur, négatif = l'inverse — voir la
 * doc de [LoanPersonBalanceItem]). Couvre aussi le même bug que [LoansViewModelTest] : la
 * répartition par statut doit recalculer le statut à la lecture, pas faire confiance à la valeur
 * persistée.
 */
class LoanStatisticsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val loanRepository: LoanRepository = mockk()
    private val personRepository: PersonRepository = mockk()
    private val accountRepository: AccountRepository = mockk()

    private val accountXof = Account(
        id = 1L, name = "Espèces XOF", icon = AccountIcon.CASH, colorArgb = 0xFF000000,
        currencyCode = "XOF", initialBalance = 0L, createdAt = 0L
    )
    private val accountEur = Account(
        id = 2L, name = "Compte EUR", icon = AccountIcon.BANK, colorArgb = 0xFF000000,
        currencyCode = "EUR", initialBalance = 0L, createdAt = 0L
    )
    private val person = Person(id = 1L, name = "Aïcha", phone = null, createdAt = 0L)

    private fun loan(
        id: Long,
        accountId: Long,
        type: LoanType,
        amount: Long,
        amountRepaid: Long,
        dueDate: Long = Long.MAX_VALUE / 2,
        status: LoanStatus = LoanStatus.ONGOING
    ) = Loan(
        id = id,
        personId = person.id,
        accountId = accountId,
        type = type,
        amount = amount,
        amountRepaid = amountRepaid,
        remainingAmount = amount - amountRepaid,
        startDate = 0L,
        dueDate = dueDate,
        reason = LoanReason.OTHER,
        reasonCustomText = null,
        repaymentMode = RepaymentMode.SINGLE,
        description = "",
        status = status,
        createdAt = 0L,
        updatedAt = 0L,
        transactionId = 0L
    )

    private fun createViewModel() = LoanStatisticsViewModel(
        loanRepository = loanRepository,
        personRepository = personRepository,
        accountRepository = accountRepository
    )

    @Test
    fun `les totaux sont regroupes par devise, jamais additionnes entre devises`() = runTest(testDispatcher) {
        val lentXof = loan(id = 1L, accountId = accountXof.id, type = LoanType.LENT, amount = 10_000L, amountRepaid = 2_000L)
        val lentEur = loan(id = 2L, accountId = accountEur.id, type = LoanType.LENT, amount = 500L, amountRepaid = 0L)
        every { loanRepository.observeLoans() } returns flowOf(listOf(lentXof, lentEur))
        every { personRepository.observePersons() } returns flowOf(listOf(person))
        every { accountRepository.observeAccounts() } returns flowOf(listOf(accountXof, accountEur))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            awaitItem() // Loading
            val state = (awaitItem() as AppResult.Success).data

            assertEquals(2, state.totalLent.size) // une entrée par devise, jamais une somme unique
            val xofTotal = state.totalLent.single { it.currencyCode == "XOF" }
            val eurTotal = state.totalLent.single { it.currencyCode == "EUR" }
            assertEquals(10_000L, xofTotal.amountMinor)
            assertEquals(500L, eurTotal.amountMinor)

            val xofRepaid = state.totalRepaidReceived.single { it.currencyCode == "XOF" }
            assertEquals(2_000L, xofRepaid.amountMinor)
        }
    }

    @Test
    fun `le solde net par personne est positif si elle doit globalement de l'argent`() = runTest(testDispatcher) {
        // Prêté (LENT) 10 000, dont 3 000 déjà remboursés -> 7 000 restants en sa faveur.
        // Emprunté (BORROWED) 2 000, rien remboursé -> 2 000 en sa défaveur.
        // Net attendu : 7 000 - 2 000 = 5 000 (positif : la personne doit globalement de l'argent).
        val lent = loan(id = 1L, accountId = accountXof.id, type = LoanType.LENT, amount = 10_000L, amountRepaid = 3_000L)
        val borrowed = loan(id = 2L, accountId = accountXof.id, type = LoanType.BORROWED, amount = 2_000L, amountRepaid = 0L)
        every { loanRepository.observeLoans() } returns flowOf(listOf(lent, borrowed))
        every { personRepository.observePersons() } returns flowOf(listOf(person))
        every { accountRepository.observeAccounts() } returns flowOf(listOf(accountXof))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            awaitItem() // Loading
            val state = (awaitItem() as AppResult.Success).data

            val balance = state.personBalances.single()
            assertEquals(person.name, balance.personName)
            assertEquals(5_000L, balance.netAmountMinor)
        }
    }

    @Test
    fun `une personne totalement soldee des deux cotes n'apparait pas dans le classement`() = runTest(testDispatcher) {
        val lent = loan(id = 1L, accountId = accountXof.id, type = LoanType.LENT, amount = 5_000L, amountRepaid = 0L)
        val borrowed = loan(id = 2L, accountId = accountXof.id, type = LoanType.BORROWED, amount = 5_000L, amountRepaid = 0L)
        every { loanRepository.observeLoans() } returns flowOf(listOf(lent, borrowed))
        every { personRepository.observePersons() } returns flowOf(listOf(person))
        every { accountRepository.observeAccounts() } returns flowOf(listOf(accountXof))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            awaitItem() // Loading
            val state = (awaitItem() as AppResult.Success).data

            assertEquals(emptyList<LoanPersonBalanceItem>(), state.personBalances)
        }
    }

    @Test
    fun `la repartition par statut recalcule le statut a la lecture`() = runTest(testDispatcher) {
        // Statut persisté volontairement faux (ONGOING) alors que l'échéance est très dépassée.
        val staleOverdue = loan(
            id = 1L,
            accountId = accountXof.id,
            type = LoanType.LENT,
            amount = 1_000L,
            amountRepaid = 0L,
            dueDate = 1_000L,
            status = LoanStatus.ONGOING
        )
        every { loanRepository.observeLoans() } returns flowOf(listOf(staleOverdue))
        every { personRepository.observePersons() } returns flowOf(listOf(person))
        every { accountRepository.observeAccounts() } returns flowOf(listOf(accountXof))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            awaitItem() // Loading
            val state = (awaitItem() as AppResult.Success).data

            val breakdown = state.statusBreakdown.single()
            assertEquals(LoanStatus.OVERDUE, breakdown.status)
            assertEquals(1, breakdown.count)
        }
    }
}
