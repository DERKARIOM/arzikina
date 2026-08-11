package com.arzikina.ne.presentation.utilities.loans

import app.cash.turbine.test
import com.arzikina.ne.MainDispatcherRule
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.AccountIcon
import com.arzikina.ne.domain.model.Person
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Vérifie la validation en 2 pages de [LoanFormViewModel] (voir sa doc), en particulier les 3 bugs
 * corrigés à l'Étape "Tests et correction des bugs" : garde anti double-soumission dans [LoanFormViewModel.save],
 * et validation de la date du premier remboursement par rapport à la date de début.
 *
 * [LoanRepository]/[PersonRepository]/[AccountRepository]/[TransactionRepository] sont de fausses
 * implémentations MockK des INTERFACES du domaine (jamais `LoanRepositoryImpl`, etc. — voir les
 * instructions du projet sur la séparation Clean Architecture) : ce ViewModel n'a donc aucune idée
 * qu'il est testé sans Room.
 */
class LoanFormViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val loanRepository: LoanRepository = mockk(relaxed = true)
    private val personRepository: PersonRepository = mockk()
    private val accountRepository: AccountRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()

    private val person = Person(id = 1L, name = "Aïcha", phone = null, createdAt = 0L)
    private val account = Account(
        id = 1L,
        name = "Espèces",
        icon = AccountIcon.CASH,
        colorArgb = 0xFF000000,
        currencyCode = "XOF",
        initialBalance = 0L,
        createdAt = 0L
    )

    @Before
    fun setUp() {
        every { personRepository.observePersons() } returns flowOf(listOf(person))
        every { accountRepository.observeAccounts() } returns flowOf(listOf(account))
        every { transactionRepository.observeTransactions() } returns flowOf(emptyList())
        coEvery { loanRepository.saveLoan(any()) } returns 42L
        coEvery { loanRepository.recordPayment(any()) } returns 99L
    }

    private fun createViewModel() = LoanFormViewModel(
        loanRepository = loanRepository,
        personRepository = personRepository,
        accountRepository = accountRepository,
        transactionRepository = transactionRepository
    )

    @Test
    fun `goToStep2 avec tous les champs vides affiche toutes les erreurs et reste a l'etape 1`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.goToStep2()

        val state = viewModel.formState.value
        assertEquals(1, state.step)
        assertNotNull(state.personError)
        assertNotNull(state.accountError)
        assertNotNull(state.amountError)
    }

    @Test
    fun `goToStep2 avec une echeance avant la date de debut affiche une erreur dediee`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onPersonSelected(person)
        viewModel.onAccountSelected(account)
        viewModel.onAmountChange("1000")
        val start = 10_000_000L
        viewModel.onStartDateChange(start)
        viewModel.onDueDateChange(start - 1L) // échéance avant le début

        viewModel.goToStep2()

        val state = viewModel.formState.value
        assertEquals(1, state.step)
        assertNotNull(state.dueDateError)
    }

    @Test
    fun `goToStep2 avec des champs valides avance a l'etape 2`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onPersonSelected(person)
        viewModel.onAccountSelected(account)
        viewModel.onAmountChange("1000")

        viewModel.goToStep2()

        assertEquals(2, viewModel.formState.value.step)
    }

    @Test
    fun `save sans premier remboursement enregistre le pret et emet Saved`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onPersonSelected(person)
        viewModel.onAccountSelected(account)
        viewModel.onAmountChange("1000")
        viewModel.goToStep2()

        viewModel.events.test {
            viewModel.save()
            advanceUntilIdle()
            assertEquals(LoanFormEvent.Saved, awaitItem())
        }

        coVerify(exactly = 1) { loanRepository.saveLoan(any()) }
        coVerify(exactly = 0) { loanRepository.recordPayment(any()) }
        assertTrue(viewModel.formState.value.isSaving.not())
    }

    @Test
    fun `save avec un premier remboursement dont la date precede le debut est refuse`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onPersonSelected(person)
        viewModel.onAccountSelected(account)
        viewModel.onAmountChange("1000")
        val start = 10_000_000L
        viewModel.onStartDateChange(start)
        viewModel.onDueDateChange(start + 1_000_000L)
        viewModel.goToStep2()
        viewModel.onFirstPaymentAmountChange("100")
        viewModel.onFirstPaymentDateChange(start - 1L) // avant le début du prêt

        viewModel.save()
        advanceUntilIdle()

        assertNotNull(viewModel.formState.value.firstPaymentDateError)
        coVerify(exactly = 0) { loanRepository.saveLoan(any()) }
    }

    @Test
    fun `save avec un premier remboursement superieur au montant total est refuse`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onPersonSelected(person)
        viewModel.onAccountSelected(account)
        viewModel.onAmountChange("1000")
        viewModel.goToStep2()
        viewModel.onFirstPaymentAmountChange("2000") // dépasse le montant total (1000)

        viewModel.save()
        advanceUntilIdle()

        assertNotNull(viewModel.formState.value.firstPaymentAmountError)
        coVerify(exactly = 0) { loanRepository.saveLoan(any()) }
    }

    @Test
    fun `save enregistre aussi le premier remboursement quand il est valide`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onPersonSelected(person)
        viewModel.onAccountSelected(account)
        viewModel.onAmountChange("1000")
        viewModel.goToStep2()
        viewModel.onFirstPaymentAmountChange("200")

        viewModel.save()
        advanceUntilIdle()

        coVerify(exactly = 1) { loanRepository.saveLoan(any()) }
        coVerify(exactly = 1) { loanRepository.recordPayment(any()) }
    }

    @Test
    fun `un double-tap rapide sur save n'enregistre le pret qu'une seule fois`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onPersonSelected(person)
        viewModel.onAccountSelected(account)
        viewModel.onAmountChange("1000")
        viewModel.goToStep2()

        // `isSaving` passe à `true` de façon SYNCHRONE (avant le `viewModelScope.launch`, voir la
        // doc de `LoanFormViewModel.save`) : un deuxième appel immédiat doit donc être ignoré,
        // sans même avoir besoin d'avancer le dispatcher de test entre les deux appels — exactement
        // le scénario d'un double-tap réel sur le bouton d'enregistrement.
        viewModel.save()
        assertTrue(viewModel.formState.value.isSaving)
        viewModel.save()

        advanceUntilIdle()

        coVerify(exactly = 1) { loanRepository.saveLoan(any()) }
    }
}
