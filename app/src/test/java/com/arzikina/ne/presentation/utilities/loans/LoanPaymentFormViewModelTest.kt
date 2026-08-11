package com.arzikina.ne.presentation.utilities.loans

import androidx.lifecycle.SavedStateHandle
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
import com.arzikina.ne.domain.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Vérifie [LoanPaymentFormViewModel] (voir sa doc), en particulier deux bugs corrigés à l'Étape
 * "Tests et correction des bugs" : garde anti double-soumission dans [LoanPaymentFormViewModel.save],
 * et [LoanPaymentFormState.notFound] (l'écran restait figé, bouton désactivé sans explication, si
 * le prêt/emprunt affiché avait été supprimé depuis un autre écran pendant que ce formulaire
 * restait ouvert).
 */
class LoanPaymentFormViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val loanRepository: LoanRepository = mockk(relaxed = true)
    private val personRepository: PersonRepository = mockk()
    private val accountRepository: AccountRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()

    private val person = Person(id = 1L, name = "Moussa", phone = null, createdAt = 0L)
    private val account = Account(
        id = 1L,
        name = "Espèces",
        icon = AccountIcon.CASH,
        colorArgb = 0xFF000000,
        currencyCode = "XOF",
        initialBalance = 0L,
        createdAt = 0L
    )
    private val loan = Loan(
        id = 5L,
        personId = person.id,
        accountId = account.id,
        type = LoanType.LENT,
        amount = 10_000L,
        amountRepaid = 4_000L,
        remainingAmount = 6_000L,
        startDate = 0L,
        dueDate = 1_000_000L,
        reason = LoanReason.OTHER,
        reasonCustomText = null,
        repaymentMode = RepaymentMode.SINGLE,
        description = "Aide",
        status = LoanStatus.ONGOING,
        createdAt = 0L,
        updatedAt = 0L,
        transactionId = 7L
    )

    private fun createViewModel(loanIdArg: Long = loan.id) = LoanPaymentFormViewModel(
        savedStateHandle = SavedStateHandle(mapOf("loanId" to loanIdArg)),
        loanRepository = loanRepository,
        personRepository = personRepository,
        accountRepository = accountRepository,
        transactionRepository = transactionRepository
    )

    private fun stubHappyPath() {
        coEvery { loanRepository.getLoan(loan.id) } returns loan
        coEvery { personRepository.getPerson(person.id) } returns person
        coEvery { accountRepository.getAccount(account.id) } returns account
        every { accountRepository.observeAccounts() } returns flowOf(listOf(account))
        every { transactionRepository.observeTransactions() } returns flowOf(emptyList())
        coEvery { loanRepository.recordPayment(any()) } returns 1L
    }

    @Test
    fun `au chargement l'etat reprend le solde restant et la devise du pret`() = runTest(testDispatcher) {
        stubHappyPath()
        val viewModel = createViewModel()

        advanceUntilIdle()

        val state = viewModel.formState.value
        assertTrue(state.isLoaded)
        assertEquals(6_000L, state.loanRemainingAmount)
        assertEquals("XOF", state.loanCurrencyCode)
        assertEquals(account.id, state.accountId)
    }

    @Test
    fun `pret introuvable declenche notFound plutot que de rester bloque`() = runTest(testDispatcher) {
        coEvery { loanRepository.getLoan(999L) } returns null
        every { accountRepository.observeAccounts() } returns flowOf(emptyList())
        every { transactionRepository.observeTransactions() } returns flowOf(emptyList())
        val viewModel = createViewModel(loanIdArg = 999L)

        advanceUntilIdle()

        val state = viewModel.formState.value
        assertTrue(state.notFound)
        assertTrue(state.isLoaded.not())
    }

    @Test
    fun `save avec un montant qui depasse le solde restant est refuse`() = runTest(testDispatcher) {
        stubHappyPath()
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAmountChange("70.00") // 7000 > 6000 (solde restant)

        viewModel.save()
        advanceUntilIdle()

        assertNotNull(viewModel.formState.value.amountError)
        coVerify(exactly = 0) { loanRepository.recordPayment(any()) }
    }

    @Test
    fun `save avant la fin du chargement (accountId encore a 0) est refuse`() = runTest(testDispatcher) {
        stubHappyPath()
        val viewModel = createViewModel()
        // Ne PAS avancer le dispatcher : le prêt n'est pas encore chargé (voir `init`), `accountId`
        // vaut donc encore sa valeur par défaut `0L` (voir `LoanPaymentFormState`) — même situation
        // qu'un appui sur "Enregistrer" avant que l'écran n'ait fini de charger le prêt/emprunt.
        viewModel.onAmountChange("20.00")

        viewModel.save()
        advanceUntilIdle()

        assertNotNull(viewModel.formState.value.accountError)
        coVerify(exactly = 0) { loanRepository.recordPayment(any()) }
    }

    @Test
    fun `save avec des donnees valides enregistre le remboursement et emet Saved`() = runTest(testDispatcher) {
        stubHappyPath()
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAmountChange("20.00")

        viewModel.events.test {
            viewModel.save()
            advanceUntilIdle()
            assertEquals(LoanPaymentFormEvent.Saved, awaitItem())
        }

        coVerify(exactly = 1) { loanRepository.recordPayment(any()) }
    }

    @Test
    fun `un double-tap rapide sur save n'enregistre le remboursement qu'une seule fois`() = runTest(testDispatcher) {
        stubHappyPath()
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAmountChange("20.00")

        viewModel.save()
        assertTrue(viewModel.formState.value.isSaving)
        viewModel.save()
        advanceUntilIdle()

        coVerify(exactly = 1) { loanRepository.recordPayment(any()) }
    }
}
