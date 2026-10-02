package com.naniger.arzikina.presentation.utilities.loans

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.naniger.arzikina.MainDispatcherRule
import com.naniger.arzikina.domain.model.Loan
import com.naniger.arzikina.domain.model.LoanGiftException
import com.naniger.arzikina.domain.model.LoanReason
import com.naniger.arzikina.domain.model.LoanStatus
import com.naniger.arzikina.domain.model.LoanType
import com.naniger.arzikina.domain.model.Person
import com.naniger.arzikina.domain.model.RepaymentMode
import com.naniger.arzikina.domain.repository.AccountRepository
import com.naniger.arzikina.domain.repository.LoanRepository
import com.naniger.arzikina.domain.repository.PersonRepository
import com.naniger.arzikina.util.AppResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** « Transformer en cadeau » côté écran Détail : actions proposées selon l'état, et issue de l'action. */
class LoanDetailViewModelGiftTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val day = 24L * 60 * 60 * 1000
    private val start = System.currentTimeMillis() - 5 * day

    private val loanRepository = mockk<LoanRepository>(relaxed = true)
    private val personRepository = mockk<PersonRepository>()
    private val accountRepository = mockk<AccountRepository>()

    private fun loan(repaid: Long = 0L, gifted: Long = 0L) = Loan(
        id = 7L, personId = 3L, accountId = 2L, type = LoanType.LENT,
        amount = 100_000L, amountRepaid = repaid, remainingAmount = 100_000L - repaid - gifted,
        startDate = start, dueDate = start + 30 * day, reason = LoanReason.OTHER,
        repaymentMode = RepaymentMode.SINGLE, status = LoanStatus.ONGOING,
        createdAt = start, updatedAt = start, transactionId = 10L,
        giftedAmount = gifted, giftTransactionId = if (gifted > 0) 11L else null, giftedAt = if (gifted > 0) start else null
    )

    private fun viewModel(loan: Loan): LoanDetailViewModel {
        every { loanRepository.observeLoans() } returns flowOf(listOf(loan))
        every { loanRepository.observePayments(7L) } returns flowOf(emptyList())
        every { personRepository.observePersons() } returns flowOf(listOf(Person(id = 3L, name = "Abdou", createdAt = start)))
        every { accountRepository.observeAccounts() } returns flowOf(emptyList())
        return LoanDetailViewModel(SavedStateHandle(mapOf("loanId" to 7L)), loanRepository, personRepository, accountRepository)
    }

    private suspend fun LoanDetailViewModel.successState(): LoanDetailUiState =
        (uiState.first { it is AppResult.Success } as AppResult.Success).data

    @Test
    fun `pret en cours - action proposee, remboursements possibles`() = runTest {
        val state = viewModel(loan()).successState()
        assertTrue(state.canConvertToGift)
        assertFalse(state.isSettled)
        assertTrue(state.canDeletePayments)
    }

    @Test
    fun `pret partiellement rembourse - action proposee`() = runTest {
        assertTrue(viewModel(loan(repaid = 40_000L)).successState().canConvertToGift)
    }

    @Test
    fun `pret entierement rembourse - action masquee`() = runTest {
        val state = viewModel(loan(repaid = 100_000L)).successState()
        assertFalse(state.canConvertToGift)
        assertTrue(state.isSettled)
    }

    @Test
    fun `pret transforme - statut GIFTED, tout verrouille`() = runTest {
        val state = viewModel(loan(repaid = 40_000L, gifted = 60_000L)).successState()
        assertEquals(LoanStatus.GIFTED, state.loan.status)
        assertFalse(state.canConvertToGift)
        assertTrue(state.isSettled)
        assertFalse(state.canDeletePayments)
    }

    @Test
    fun `transformation reussie - description transmise et evenement de succes`() = runTest {
        val vm = viewModel(loan())
        coEvery { loanRepository.convertToGift(7L, "Cadeau à Abdou") } returns 10L
        vm.events.test {
            vm.convertToGift("Cadeau à Abdou")
            assertEquals(LoanDetailEvent.ConvertedToGift, awaitItem())
        }
        coVerify(exactly = 1) { loanRepository.convertToGift(7L, "Cadeau à Abdou") }
    }

    @Test
    fun `transformation refusee - evenement dedie, pas de plantage`() = runTest {
        val vm = viewModel(loan())
        coEvery { loanRepository.convertToGift(any(), any()) } throws LoanGiftException.NotConvertible(7L)
        vm.events.test {
            vm.convertToGift("Cadeau à Abdou")
            assertEquals(LoanDetailEvent.GiftNotAllowed, awaitItem())
        }
    }
}
