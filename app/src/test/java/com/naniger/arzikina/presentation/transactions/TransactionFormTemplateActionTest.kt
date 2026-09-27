package com.naniger.arzikina.presentation.transactions

import app.cash.turbine.test
import com.naniger.arzikina.MainDispatcherRule
import com.naniger.arzikina.domain.model.FeeType
import com.naniger.arzikina.domain.model.Transaction
import com.naniger.arzikina.domain.model.TransactionTemplate
import com.naniger.arzikina.domain.model.TransactionType
import com.naniger.arzikina.domain.repository.AccountRepository
import com.naniger.arzikina.domain.repository.CategoryRepository
import com.naniger.arzikina.domain.repository.LoanRepository
import com.naniger.arzikina.domain.repository.TransactionRepository
import com.naniger.arzikina.domain.repository.TransactionTemplateRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Menu ⋮ d'une transaction : « Créer un modèle » / « Voir le modèle » (voir [TemplateActionState]). */
class TransactionFormTemplateActionTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val transactionRepository: TransactionRepository = mockk(relaxed = true)
    private val accountRepository: AccountRepository = mockk()
    private val categoryRepository: CategoryRepository = mockk()
    private val loanRepository: LoanRepository = mockk()
    private val templateRepository: TransactionTemplateRepository = mockk()
    private val linkedTemplate = MutableStateFlow<TransactionTemplate?>(null)

    private val courses = Transaction(
        id = 42L,
        amount = 1_000_000L,
        type = TransactionType.EXPENSE,
        accountId = 3L,
        categoryId = 7L,
        date = 1_790_000_000_000L,
        description = "Courses du mois",
        createdAt = 1_789_000_000_000L
    )

    @Before
    fun setUp() {
        every { accountRepository.observeAccounts() } returns flowOf(emptyList())
        every { transactionRepository.observeTransactions() } returns flowOf(emptyList())
        every { categoryRepository.observeCategories() } returns flowOf(emptyList())
        every { categoryRepository.observeCategoriesByType(any()) } returns flowOf(emptyList())
        every { templateRepository.observeTemplates() } returns flowOf(emptyList())
        every { templateRepository.observeTemplateCreatedFromTransaction(42L) } returns linkedTemplate
        coEvery { loanRepository.findLoanIdForTransaction(any()) } returns null
        coEvery { transactionRepository.getTransaction(42L) } returns courses
    }

    private fun createViewModel(transactionId: Long = 42L) = TransactionFormViewModel(
        savedStateHandle = TransactionFormFragmentArgs(transactionId = transactionId).toSavedStateHandle(),
        transactionRepository = transactionRepository,
        accountRepository = accountRepository,
        categoryRepository = categoryRepository,
        loanRepository = loanRepository,
        templateRepository = templateRepository
    )

    @Test
    fun `TEST 1 puis 5 - Creer un modele, puis Voir le modele une fois le modele cree`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.templateAction.test {
            assertEquals(TemplateActionState.Hidden, awaitItem())
            advanceUntilIdle()
            assertEquals(TemplateActionState.CanCreate, awaitItem())

            linkedTemplate.value = mockk { every { id } returns 100L }
            advanceUntilIdle()
            assertEquals(TemplateActionState.Linked(100L), awaitItem())

            // Modèle supprimé : l'action redevient disponible.
            linkedTemplate.value = null
            advanceUntilIdle()
            assertEquals(TemplateActionState.CanCreate, awaitItem())
        }
    }

    @Test
    fun `revenu - action disponible`() = runTest(testDispatcher) {
        coEvery { transactionRepository.getTransaction(42L) } returns courses.copy(type = TransactionType.INCOME)
        assertAction(TemplateActionState.CanCreate)
    }

    @Test
    fun `transfert - aucune action`() = runTest(testDispatcher) {
        coEvery { transactionRepository.getTransaction(42L) } returns
            courses.copy(type = TransactionType.TRANSFER, categoryId = null, transferAccountId = 4L)
        assertAction(TemplateActionState.Hidden)
    }

    @Test
    fun `ligne de frais - aucune action`() = runTest(testDispatcher) {
        coEvery { transactionRepository.getTransaction(42L) } returns courses.copy(feeType = FeeType.entries.first())
        assertAction(TemplateActionState.Hidden)
    }

    @Test
    fun `transaction avec frais - action disponible (le modele reprend le montant principal)`() = runTest(testDispatcher) {
        coEvery { transactionRepository.getTransaction(42L) } returns courses.copy(feeTransactionId = 43L)
        coEvery { transactionRepository.getTransaction(43L) } returns null
        assertAction(TemplateActionState.CanCreate)
    }

    @Test
    fun `transaction liee a un pret - aucune action`() = runTest(testDispatcher) {
        coEvery { loanRepository.findLoanIdForTransaction(42L) } returns 9L
        assertAction(TemplateActionState.Hidden)
    }

    @Test
    fun `creation d une transaction - aucune action`() = runTest(testDispatcher) {
        val viewModel = createViewModel(transactionId = 0L)
        viewModel.templateAction.test {
            advanceUntilIdle()
            assertEquals(TemplateActionState.Hidden, expectMostRecentItem())
        }
    }

    private suspend fun kotlinx.coroutines.test.TestScope.assertAction(expected: TemplateActionState) {
        val viewModel = createViewModel()
        viewModel.templateAction.test {
            advanceUntilIdle()
            assertEquals(expected, expectMostRecentItem())
        }
    }
}
