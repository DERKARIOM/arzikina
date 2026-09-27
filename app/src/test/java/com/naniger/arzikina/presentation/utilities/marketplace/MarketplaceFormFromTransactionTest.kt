package com.naniger.arzikina.presentation.utilities.marketplace

import app.cash.turbine.test
import com.naniger.arzikina.MainDispatcherRule
import com.naniger.arzikina.domain.model.Category
import com.naniger.arzikina.domain.model.TemplateAlreadyLinkedException
import com.naniger.arzikina.domain.model.Transaction
import com.naniger.arzikina.domain.model.TransactionTemplate
import com.naniger.arzikina.domain.model.TransactionType
import com.naniger.arzikina.domain.repository.AccountRepository
import com.naniger.arzikina.domain.repository.CategoryRepository
import com.naniger.arzikina.domain.repository.TransactionRepository
import com.naniger.arzikina.domain.repository.TransactionTemplateRepository
import com.naniger.arzikina.presentation.components.DefaultNameLocalizer
import com.naniger.arzikina.util.Money
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * « Créer un modèle à partir de cette transaction » côté formulaire de modèle : préremplissage,
 * modifications avant confirmation, relation enregistrée, transaction jamais modifiée, aucun doublon.
 */
class MarketplaceFormFromTransactionTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val templateRepository: TransactionTemplateRepository = mockk(relaxed = true)
    private val accountRepository: AccountRepository = mockk()
    private val categoryRepository: CategoryRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk(relaxed = true)
    private val defaultNameLocalizer: DefaultNameLocalizer = mockk()
    private val saved = slot<TransactionTemplate>()

    private val franc = Money.MINOR_UNITS_PER_MAJOR.toLong()

    private val courses = Transaction(
        id = 42L,
        amount = 10_000 * franc,
        type = TransactionType.EXPENSE,
        accountId = 3L,
        categoryId = 7L,
        date = 1_790_000_000_000L,
        description = "Courses du mois",
        createdAt = 1_789_000_000_000L
    )
    private val alimentation = mockk<Category>(relaxed = true)

    @Before
    fun setUp() {
        every { accountRepository.observeAccounts() } returns flowOf(emptyList())
        every { transactionRepository.observeTransactions() } returns flowOf(emptyList())
        every { categoryRepository.observeCategoriesByType(any()) } returns flowOf(emptyList())
        coEvery { categoryRepository.getCategory(7L) } returns alimentation
        every { defaultNameLocalizer.displayName(alimentation) } returns "Alimentation"
        coEvery { transactionRepository.getTransaction(42L) } returns courses
        coEvery { templateRepository.getTemplateCreatedFromTransaction(any()) } returns null
        coEvery { templateRepository.saveTemplate(capture(saved)) } returns 100L
    }

    private fun createViewModel(templateId: Long = 0L, sourceTransactionId: Long = 42L) = MarketplaceFormViewModel(
        savedStateHandle = MarketplaceFormFragmentArgs(templateId = templateId, sourceTransactionId = sourceTransactionId).toSavedStateHandle(),
        templateRepository = templateRepository,
        accountRepository = accountRepository,
        categoryRepository = categoryRepository,
        transactionRepository = transactionRepository,
        defaultNameLocalizer = defaultNameLocalizer
    )

    @Test
    fun `TEST 2 - formulaire prerempli avec les donnees de la transaction`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.formState.value
        assertTrue(viewModel.isFromTransaction)
        assertEquals("Courses du mois", state.name)
        assertEquals(TransactionType.EXPENSE, state.type)
        assertEquals(Money.formatForInput(10_000 * franc), state.amountInput)
        assertEquals(7L, state.categoryId)
        assertEquals(3L, state.accountId)
        assertEquals("Courses du mois", state.description)
        // Jamais de date/heure figée reprise de la transaction.
        assertFalse(state.hasDefaultTime)
        // Rien n'est créé tant que l'utilisateur n'a pas confirmé.
        coVerify(exactly = 0) { templateRepository.saveTemplate(any()) }
    }

    @Test
    fun `TEST 3 et 4 - nom et montant modifies, modele cree et lie, transaction inchangee`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.onNameChange("Courses")
            viewModel.onAmountChange("15000")
            viewModel.save()
            advanceUntilIdle()
            assertEquals(MarketplaceFormEvent.Saved, awaitItem())
        }

        val template = saved.captured
        assertEquals(0L, template.id)
        assertEquals("Courses", template.name)
        assertEquals(15_000 * franc, template.amount)
        assertEquals(42L, template.sourceTransactionId)
        assertNull(template.defaultHour)
        // La transaction d'origine n'est que LUE : jamais enregistrée, supprimée ni recréée.
        coVerify(exactly = 0) { transactionRepository.saveTransaction(any(), any()) }
        coVerify(exactly = 0) { transactionRepository.deleteTransaction(any()) }
    }

    @Test
    fun `TEST 5 - transaction deja liee - aucun second modele, modele existant propose`() = runTest(testDispatcher) {
        coEvery { templateRepository.getTemplateCreatedFromTransaction(42L) } returns mockk { every { id } returns 55L }
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(55L, viewModel.formState.value.alreadyLinkedTemplateId)
        assertEquals("", viewModel.formState.value.name)
    }

    @Test
    fun `doublon detecte au moment d enregistrer (double validation, synchronisation)`() = runTest(testDispatcher) {
        coEvery { templateRepository.saveTemplate(any()) } throws TemplateAlreadyLinkedException(55L)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.save()
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(55L, viewModel.formState.value.alreadyLinkedTemplateId)
    }

    @Test
    fun `revenu - categorie preremplie conservee quand le formulaire coche le type`() = runTest(testDispatcher) {
        coEvery { transactionRepository.getTransaction(42L) } returns courses.copy(type = TransactionType.INCOME, description = "Salaire")
        val viewModel = createViewModel()
        advanceUntilIdle()

        // MarketplaceFormFragment.render coche le bouton du type courant, ce qui rappelle onTypeChange.
        viewModel.onTypeChange(TransactionType.INCOME)
        assertEquals(TransactionType.INCOME, viewModel.formState.value.type)
        assertEquals(7L, viewModel.formState.value.categoryId)
    }

    @Test
    fun `transfert ou transaction supprimee - rien n est prerempli`() = runTest(testDispatcher) {
        coEvery { transactionRepository.getTransaction(42L) } returns courses.copy(type = TransactionType.TRANSFER, categoryId = null, transferAccountId = 4L)
        val transfer = createViewModel()
        advanceUntilIdle()
        assertTrue(transfer.formState.value.sourceTransactionUnavailable)

        coEvery { transactionRepository.getTransaction(42L) } returns null
        val deleted = createViewModel()
        advanceUntilIdle()
        assertTrue(deleted.formState.value.sourceTransactionUnavailable)
    }

    @Test
    fun `edition d un modele - source ignoree, relation jamais modifiee par le formulaire`() = runTest(testDispatcher) {
        coEvery { templateRepository.getTemplate(100L) } returns TransactionTemplate(
            id = 100L, name = "Courses", type = TransactionType.EXPENSE, amount = 15_000 * franc,
            categoryId = 7L, accountId = 3L, createdAt = 1L, updatedAt = 1L, sourceTransactionId = 42L
        )
        val viewModel = createViewModel(templateId = 100L, sourceTransactionId = 42L)
        advanceUntilIdle()

        assertFalse(viewModel.isFromTransaction)
        viewModel.save()
        advanceUntilIdle()
        // Le repository conserve la relation enregistrée (voir saveTemplate) : le formulaire
        // d'édition n'envoie jamais de nouvelle source.
        assertNull(saved.captured.sourceTransactionId)
        coVerify(exactly = 0) { transactionRepository.getTransaction(any()) }
    }
}
