package com.naniger.arzikina.presentation.accounts

import app.cash.turbine.test
import com.naniger.arzikina.MainDispatcherRule
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.AccountIcon
import com.naniger.arzikina.domain.model.AccountType
import com.naniger.arzikina.domain.repository.AccountRepository
import com.naniger.arzikina.presentation.components.DefaultNameLocalizer
import com.naniger.arzikina.util.Money
import com.naniger.arzikina.util.external.ExternalAppLauncher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Objectif d'épargne comme TYPE de compte (voir `AccountType.SAVINGS_GOAL`) : création, montant
 * cible obligatoire, transformation compte classique ↔ objectif SANS recréer le compte (même id,
 * même solde initial, même position — donc mêmes transactions), confirmation avant de retirer un
 * objectif.
 */
class AccountFormViewModelSavingsGoalTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val accountRepository: AccountRepository = mockk(relaxed = true)
    private val externalAppLauncher: ExternalAppLauncher = mockk(relaxed = true)
    private val defaultNameLocalizer: DefaultNameLocalizer = mockk()
    private val saved = slot<Account>()

    private val franc = Money.MINOR_UNITS_PER_MAJOR.toLong()

    private val classicAccount = Account(
        id = 7L,
        name = "Épargne moto",
        icon = AccountIcon.BANK,
        colorArgb = 0xFF3B82F6L,
        currencyCode = "XOF",
        initialBalance = 150_000 * franc,
        createdAt = 1_000L,
        type = AccountType.BANK,
        displayOrder = 3L
    )

    private val goalAccount = classicAccount.copy(
        type = AccountType.SAVINGS_GOAL,
        savingsTargetAmount = 500_000 * franc,
        savingsDescription = "Pour aller au travail"
    )

    @Before
    fun setUp() {
        every { defaultNameLocalizer.displayName(any<Account>()) } answers { firstArg<Account>().name }
        every { defaultNameLocalizer.canonicalAccountName(any()) } answers { firstArg() }
        coEvery { accountRepository.saveAccount(capture(saved)) } answers { saved.captured.id.takeIf { it != 0L } ?: 99L }
    }

    private fun createViewModel(accountId: Long = 0L) = AccountFormViewModel(
        savedStateHandle = AccountFormFragmentArgs(accountId = accountId).toSavedStateHandle(),
        accountRepository = accountRepository,
        externalAppLauncher = externalAppLauncher,
        ioDispatcher = testDispatcher,
        defaultNameLocalizer = defaultNameLocalizer
    )

    @Test
    fun `creation d un objectif sans montant cible - erreur et rien n est enregistre`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onNameChange("Acheter une moto")
        viewModel.onTypeChange(AccountType.SAVINGS_GOAL)

        viewModel.save()
        advanceUntilIdle()

        assertEquals(R.string.account_form_savings_target_error, viewModel.formState.value.savingsTargetError)
        coVerify(exactly = 0) { accountRepository.saveAccount(any()) }
    }

    @Test
    fun `creation d un objectif avec montant cible nul refusee`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onNameChange("Acheter une moto")
        viewModel.onTypeChange(AccountType.SAVINGS_GOAL)
        viewModel.onSavingsTargetChange("0")

        viewModel.save()
        advanceUntilIdle()

        assertEquals(R.string.account_form_savings_target_error, viewModel.formState.value.savingsTargetError)
        coVerify(exactly = 0) { accountRepository.saveAccount(any()) }
    }

    @Test
    fun `creation d un objectif - type, cible, solde initial et description enregistres`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onNameChange("Acheter une moto")
        viewModel.onTypeChange(AccountType.SAVINGS_GOAL)
        viewModel.onInitialBalanceChange("150000")
        viewModel.onSavingsTargetChange("500000")
        viewModel.onSavingsDescriptionChange("  Pour aller au travail  ")

        viewModel.save()
        advanceUntilIdle()

        val account = saved.captured
        assertEquals(0L, account.id)
        assertEquals(AccountType.SAVINGS_GOAL, account.type)
        assertEquals(AccountIcon.SAVINGS, account.icon)
        assertEquals(150_000 * franc, account.initialBalance)
        assertEquals(500_000 * franc, account.savingsTargetAmount)
        assertEquals("Pour aller au travail", account.savingsDescription)
    }

    @Test
    fun `compte classique transforme en objectif - meme compte, meme solde, icone conservee`() = runTest(testDispatcher) {
        coEvery { accountRepository.getAccount(7L) } returns classicAccount
        val viewModel = createViewModel(accountId = 7L)
        advanceUntilIdle()

        viewModel.onTypeChange(AccountType.SAVINGS_GOAL)
        viewModel.onSavingsTargetChange("500000")
        viewModel.save()
        advanceUntilIdle()

        val account = saved.captured
        assertEquals(7L, account.id)
        assertEquals(AccountType.SAVINGS_GOAL, account.type)
        assertEquals(150_000 * franc, account.initialBalance)
        assertEquals(1_000L, account.createdAt)
        assertEquals(AccountIcon.BANK, account.icon)
        assertEquals(0xFF3B82F6L, account.colorArgb)
        assertEquals(500_000 * franc, account.savingsTargetAmount)
        coVerify(exactly = 0) { accountRepository.deleteAccount(any()) }
    }

    @Test
    fun `edition d un objectif - champs pre-remplis et cible modifiable`() = runTest(testDispatcher) {
        coEvery { accountRepository.getAccount(7L) } returns goalAccount
        val viewModel = createViewModel(accountId = 7L)
        advanceUntilIdle()

        assertEquals(Money.formatForInput(500_000 * franc), viewModel.formState.value.savingsTargetInput)
        assertEquals("Pour aller au travail", viewModel.formState.value.savingsDescriptionInput)

        viewModel.onSavingsTargetChange("750000")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(7L, saved.captured.id)
        assertEquals(750_000 * franc, saved.captured.savingsTargetAmount)
    }

    @Test
    fun `objectif repasse en compte classique - confirmation demandee avant tout enregistrement`() = runTest(testDispatcher) {
        coEvery { accountRepository.getAccount(7L) } returns goalAccount
        val viewModel = createViewModel(accountId = 7L)
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.onTypeChange(AccountType.BANK)
            viewModel.save()
            advanceUntilIdle()

            assertEquals(AccountFormEvent.ConfirmSavingsGoalRemoval, awaitItem())
            coVerify(exactly = 0) { accountRepository.saveAccount(any()) }

            viewModel.save(confirmedSavingsGoalRemoval = true)
            advanceUntilIdle()
            assertEquals(AccountFormEvent.Saved, awaitItem())
        }

        val account = saved.captured
        assertEquals(7L, account.id)
        assertEquals(AccountType.BANK, account.type)
        assertEquals(150_000 * franc, account.initialBalance)
        assertNull(account.savingsTargetAmount)
        assertNull(account.savingsDescription)
    }

    @Test
    fun `compte classique modifie - aucune confirmation ni donnee d objectif`() = runTest(testDispatcher) {
        coEvery { accountRepository.getAccount(7L) } returns classicAccount
        val viewModel = createViewModel(accountId = 7L)
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.onNameChange("Banque principale")
            viewModel.save()
            advanceUntilIdle()
            assertEquals(AccountFormEvent.Saved, awaitItem())
        }
        assertNull(saved.captured.savingsTargetAmount)
        assertEquals(AccountType.BANK, saved.captured.type)
    }
}
