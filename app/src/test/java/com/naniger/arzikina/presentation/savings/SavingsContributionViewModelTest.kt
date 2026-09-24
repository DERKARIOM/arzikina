package com.naniger.arzikina.presentation.savings

import androidx.lifecycle.SavedStateHandle
import com.naniger.arzikina.MainDispatcherRule
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.CurrencyAmount
import com.naniger.arzikina.domain.model.SavingsGoal
import com.naniger.arzikina.domain.repository.SavingsGoalRepository
import com.naniger.arzikina.util.ContributionPreview
import com.naniger.arzikina.util.ContributionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavingsContributionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** 1 F CFA = 100 unités mineures. */
    private fun f(major: Long) = major * 100

    private val goal = SavingsGoal(
        id = GOAL_ID,
        name = "Tabaski 2027",
        targetAmount = f(150_000),
        currentAmount = f(140_000),
        currencyCode = "XOF",
        deadline = null,
        createdAt = 0L
    )

    private fun viewModel(repository: FakeSavingsGoalRepository) = SavingsContributionViewModel(
        savedStateHandle = SavedStateHandle(mapOf("savingsGoalId" to GOAL_ID)),
        savingsGoalRepository = repository
    )

    @Test
    fun `charge l objectif et propose les raccourcis de sa devise`() = runTest {
        val viewModel = viewModel(FakeSavingsGoalRepository(goal))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(goal, state.goal)
        assertEquals(listOf(f(5_000), f(10_000), f(25_000)), state.quickAmounts)
        assertEquals(ContributionPreview.Empty, state.preview)
        assertFalse(state.canConfirm)
    }

    @Test
    fun `versement ordinaire - enregistre puis ferme le panneau`() = runTest {
        val repository = FakeSavingsGoalRepository(goal)
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onAmountChange("5 000")
        assertEquals(f(5_000), viewModel.state.value.selectedQuickAmount)
        viewModel.confirm()
        advanceUntilIdle()

        assertEquals(listOf(GOAL_ID to f(5_000)), repository.contributions)
        assertEquals(SavingsContributionEvent.Close, viewModel.events.first())
        assertNull(viewModel.state.value.celebration)
    }

    @Test
    fun `versement qui atteint la cible - celebration au lieu de fermer`() = runTest {
        val repository = FakeSavingsGoalRepository(goal)
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onAmountChange("10 000")
        assertTrue((viewModel.state.value.preview as ContributionPreview.Valid).reachesTarget)
        viewModel.confirm()
        advanceUntilIdle()

        assertEquals(listOf(GOAL_ID to f(10_000)), repository.contributions)
        assertEquals(
            SavingsCelebration("Tabaski 2027", CurrencyAmount("XOF", f(150_000))),
            viewModel.state.value.celebration
        )
    }

    @Test
    fun `retrait superieur a l epargne - bloque, rien n est enregistre`() = runTest {
        val repository = FakeSavingsGoalRepository(goal)
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onTypeChange(ContributionType.WITHDRAWAL)
        viewModel.onAmountChange("150 000")
        assertEquals(ContributionPreview.ExceedsSaved(f(140_000)), viewModel.state.value.preview)
        assertFalse(viewModel.state.value.canConfirm)

        viewModel.confirm()
        advanceUntilIdle()
        assertTrue(repository.contributions.isEmpty())
    }

    @Test
    fun `retrait valide - delta negatif`() = runTest {
        val repository = FakeSavingsGoalRepository(goal)
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onTypeChange(ContributionType.WITHDRAWAL)
        viewModel.onAmountChange("40 000")
        viewModel.confirm()
        advanceUntilIdle()

        assertEquals(listOf(GOAL_ID to -f(40_000)), repository.contributions)
    }

    @Test
    fun `echec d enregistrement - message d erreur, panneau reste ouvert`() = runTest {
        val repository = FakeSavingsGoalRepository(goal, failOnContribution = true)
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onAmountChange("5 000")
        viewModel.confirm()
        advanceUntilIdle()

        assertEquals(R.string.error_generic, viewModel.state.value.errorRes)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun `objectif introuvable - fermeture`() = runTest {
        val viewModel = viewModel(FakeSavingsGoalRepository(goal = null))
        advanceUntilIdle()

        assertEquals(SavingsContributionEvent.Close, viewModel.events.first())
    }

    private class FakeSavingsGoalRepository(
        private val goal: SavingsGoal?,
        private val failOnContribution: Boolean = false
    ) : SavingsGoalRepository {

        val contributions = mutableListOf<Pair<Long, Long>>()

        override fun observeSavingsGoals(): Flow<List<SavingsGoal>> = flowOf(listOfNotNull(goal))

        override suspend fun getSavingsGoal(id: Long): SavingsGoal? = goal?.takeIf { it.id == id }

        override suspend fun saveSavingsGoal(goal: SavingsGoal) = Unit

        override suspend fun addContribution(id: Long, amountDelta: Long) {
            if (failOnContribution) error("Échec simulé")
            contributions += id to amountDelta
        }

        override suspend fun deleteSavingsGoal(id: Long) = Unit
    }

    private companion object {
        const val GOAL_ID = 7L
    }
}
