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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Vérifie [LoansViewModel] : filtres (type/statut/recherche) et, surtout, le bug corrigé à l'Étape
 * "Tests et correction des bugs" où le statut affiché/filtré venait de [Loan.status] PERSISTÉ (donc
 * potentiellement périmé) plutôt que d'être recalculé à la lecture (voir le premier test ci-dessous).
 */
class LoansViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val loanRepository: LoanRepository = mockk()
    private val personRepository: PersonRepository = mockk()
    private val accountRepository: AccountRepository = mockk()

    private val account = Account(
        id = 1L,
        name = "Espèces",
        icon = AccountIcon.CASH,
        colorArgb = 0xFF000000,
        currencyCode = "XOF",
        initialBalance = 0L,
        createdAt = 0L
    )
    private val personA = Person(id = 1L, name = "Aïcha", phone = null, createdAt = 0L)
    private val personB = Person(id = 2L, name = "Boubacar", phone = null, createdAt = 0L)

    private fun loan(
        id: Long,
        personId: Long,
        type: LoanType,
        dueDate: Long,
        storedStatus: LoanStatus,
        description: String = ""
    ) = Loan(
        id = id,
        personId = personId,
        accountId = account.id,
        type = type,
        amount = 5_000L,
        amountRepaid = 0L,
        remainingAmount = 5_000L,
        startDate = 0L,
        dueDate = dueDate,
        reason = LoanReason.OTHER,
        reasonCustomText = null,
        repaymentMode = RepaymentMode.SINGLE,
        description = description,
        status = storedStatus, // voir la doc de la classe : volontairement incohérent avec dueDate
        createdAt = 0L,
        updatedAt = 0L,
        transactionId = 0L
    )

    private fun createViewModel() = LoansViewModel(
        loanRepository = loanRepository,
        personRepository = personRepository,
        accountRepository = accountRepository
    )

    @Test
    fun `le statut persiste peut etre perime, le filtre OVERDUE doit quand meme le trouver`() = runTest(testDispatcher) {
        // `storedStatus = ONGOING` alors que `dueDate` est très dans le passé : simule un prêt qui
        // a franchi son échéance sans qu'aucune écriture n'ait recalculé son statut persisté (voir
        // la doc de `LoanStatus`, corrigé pour être recalculé à CHAQUE lecture).
        val overdueLoan = loan(
            id = 1L,
            personId = personA.id,
            type = LoanType.LENT,
            dueDate = 1_000L, // très dans le passé
            storedStatus = LoanStatus.ONGOING
        )
        every { loanRepository.observeLoans() } returns flowOf(listOf(overdueLoan))
        every { personRepository.observePersons() } returns flowOf(listOf(personA))
        every { accountRepository.observeAccounts() } returns flowOf(listOf(account))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle() // laisse le `combine` (viewModelScope, StandardTestDispatcher) tourner
            awaitItem() // AppResult.Loading

            val success = awaitItem() as AppResult.Success
            // Le statut AFFICHÉ (avant tout filtre) doit déjà être recalculé, pas la valeur périmée.
            assertEquals(LoanStatus.OVERDUE, success.data.items.single().status)

            viewModel.onStatusFilterChange(LoanStatusFilterOption.OVERDUE)
            advanceUntilIdle()
            val filtered = awaitItem() as AppResult.Success
            assertEquals(1, filtered.data.items.size)

            viewModel.onStatusFilterChange(LoanStatusFilterOption.REPAID)
            advanceUntilIdle()
            val emptyResult = awaitItem() as AppResult.Success
            assertTrue(emptyResult.data.items.isEmpty())
        }
    }

    @Test
    fun `le filtre par type restreint la liste sans affecter le resume`() = runTest(testDispatcher) {
        val lent = loan(id = 1L, personId = personA.id, type = LoanType.LENT, dueDate = Long.MAX_VALUE / 2, storedStatus = LoanStatus.ONGOING)
        val borrowed = loan(id = 2L, personId = personB.id, type = LoanType.BORROWED, dueDate = Long.MAX_VALUE / 2, storedStatus = LoanStatus.ONGOING)
        every { loanRepository.observeLoans() } returns flowOf(listOf(lent, borrowed))
        every { personRepository.observePersons() } returns flowOf(listOf(personA, personB))
        every { accountRepository.observeAccounts() } returns flowOf(listOf(account))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            awaitItem() // Loading
            val all = awaitItem() as AppResult.Success
            assertEquals(2, all.data.items.size)
            assertEquals(2, all.data.summary.totalCount)

            viewModel.onTypeFilterChange(LoanTypeFilterOption.LENT)
            advanceUntilIdle()
            val filtered = awaitItem() as AppResult.Success
            assertEquals(1, filtered.data.items.size)
            assertEquals(LoanType.LENT, filtered.data.items.single().type)
            // Voir la doc de LoansUiState.summary : le résumé reste stable, indépendant du filtre.
            assertEquals(2, filtered.data.summary.totalCount)
        }
    }

    @Test
    fun `la recherche filtre par nom de personne ou titre, insensible a la casse`() = runTest(testDispatcher) {
        val loanForA = loan(id = 1L, personId = personA.id, type = LoanType.LENT, dueDate = Long.MAX_VALUE / 2, storedStatus = LoanStatus.ONGOING, description = "Loyer")
        val loanForB = loan(id = 2L, personId = personB.id, type = LoanType.LENT, dueDate = Long.MAX_VALUE / 2, storedStatus = LoanStatus.ONGOING, description = "Moto")
        every { loanRepository.observeLoans() } returns flowOf(listOf(loanForA, loanForB))
        every { personRepository.observePersons() } returns flowOf(listOf(personA, personB))
        every { accountRepository.observeAccounts() } returns flowOf(listOf(account))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            awaitItem() // Loading
            awaitItem() // état initial (2 éléments)

            viewModel.onQueryChange("aïcha")
            advanceUntilIdle()
            val byPersonName = awaitItem() as AppResult.Success
            assertEquals(1, byPersonName.data.items.size)
            assertEquals(personA.name, byPersonName.data.items.single().personName)

            viewModel.onQueryChange("moto")
            advanceUntilIdle()
            val byTitle = awaitItem() as AppResult.Success
            assertEquals(1, byTitle.data.items.size)
            assertEquals("Moto", byTitle.data.items.single().title)
        }
    }

    @Test
    fun `resetFilters revient a ALL sans effacer la recherche`() = runTest(testDispatcher) {
        val lent = loan(id = 1L, personId = personA.id, type = LoanType.LENT, dueDate = Long.MAX_VALUE / 2, storedStatus = LoanStatus.ONGOING)
        every { loanRepository.observeLoans() } returns flowOf(listOf(lent))
        every { personRepository.observePersons() } returns flowOf(listOf(personA))
        every { accountRepository.observeAccounts() } returns flowOf(listOf(account))
        val viewModel = createViewModel()

        viewModel.onTypeFilterChange(LoanTypeFilterOption.BORROWED)
        viewModel.onQueryChange("test")
        assertTrue(viewModel.filters.value.hasActiveFilters)

        viewModel.resetFilters()

        val filters = viewModel.filters.value
        assertEquals(LoanTypeFilterOption.ALL, filters.type)
        assertEquals(LoanStatusFilterOption.ALL, filters.status)
        assertEquals("test", filters.query) // voir la doc de LoanFilters.hasActiveFilters
    }
}
