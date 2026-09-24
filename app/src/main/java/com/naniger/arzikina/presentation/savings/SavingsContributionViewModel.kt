package com.naniger.arzikina.presentation.savings

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.CurrencyAmount
import com.naniger.arzikina.domain.model.SavingsGoal
import com.naniger.arzikina.domain.repository.SavingsGoalRepository
import com.naniger.arzikina.util.ContributionPreview
import com.naniger.arzikina.util.ContributionType
import com.naniger.arzikina.util.Money
import com.naniger.arzikina.util.SavingsContribution
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Objectif qui vient d'être atteint : contenu de l'écran de célébration du panneau. */
data class SavingsCelebration(val goalName: String, val savedAmount: CurrencyAmount)

/**
 * État du panneau « Ajouter / Retirer ». [amountInput] reste le texte saisi (formaté en direct par
 * `MoneyInputFormatter`) ; [preview] est recalculé à chaque changement (voir [SavingsContribution]).
 *
 * [celebration] fait partie de l'état, pas d'un événement : l'écran « Objectif atteint » doit
 * survivre à une rotation pendant qu'il est affiché.
 */
data class SavingsContributionState(
    val goal: SavingsGoal? = null,
    val type: ContributionType = ContributionType.DEPOSIT,
    val amountInput: String = "",
    val preview: ContributionPreview = ContributionPreview.Empty,
    val quickAmounts: List<Long> = emptyList(),
    val isSaving: Boolean = false,
    @StringRes val errorRes: Int? = null,
    val celebration: SavingsCelebration? = null
) {
    /** Rien à retirer tant que l'objectif est à 0 : le bouton « Retirer » est alors désactivé. */
    val canWithdraw: Boolean get() = (goal?.currentAmount ?: 0L) > 0L

    val canConfirm: Boolean get() = preview is ContributionPreview.Valid && !isSaving

    /** Raccourci correspondant au montant saisi, pour le cocher dans la rangée de raccourcis. */
    val selectedQuickAmount: Long? get() = Money.parseToMinorUnits(amountInput)?.takeIf { it in quickAmounts }
}

/** Seul événement ponctuel : fermer le panneau (mouvement enregistré, ou objectif introuvable). */
sealed interface SavingsContributionEvent {
    data object Close : SavingsContributionEvent
}

@HiltViewModel
class SavingsContributionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val savingsGoalRepository: SavingsGoalRepository
) : ViewModel() {

    private val goalId: Long =
        SavingsContributionBottomSheetArgs.fromSavedStateHandle(savedStateHandle).savingsGoalId

    private val _state = MutableStateFlow(SavingsContributionState())
    val state: StateFlow<SavingsContributionState> = _state.asStateFlow()

    // Channel plutôt que SharedFlow : un « Close » émis pendant une rotation (aucun collecteur)
    // est conservé jusqu'au retour de l'écran au lieu d'être perdu.
    private val _events = Channel<SavingsContributionEvent>(Channel.BUFFERED)
    val events: Flow<SavingsContributionEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val goal = savingsGoalRepository.getSavingsGoal(goalId)
            if (goal == null) {
                _events.send(SavingsContributionEvent.Close)
            } else {
                updateState {
                    it.copy(goal = goal, quickAmounts = SavingsContribution.quickAmounts(goal.currencyCode))
                }
            }
        }
    }

    fun onTypeChange(type: ContributionType) = updateState { it.copy(type = type) }

    fun onAmountChange(value: String) = updateState { it.copy(amountInput = value) }

    fun confirm() {
        val current = _state.value
        val goal = current.goal ?: return
        val preview = current.preview as? ContributionPreview.Valid ?: return
        if (current.isSaving) return

        _state.update { it.copy(isSaving = true, errorRes = null) }
        viewModelScope.launch {
            val saved = try {
                savingsGoalRepository.addContribution(goal.id, preview.delta)
                true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                false
            }
            when {
                !saved -> _state.update { it.copy(isSaving = false, errorRes = R.string.error_generic) }
                preview.reachesTarget -> _state.update {
                    it.copy(
                        isSaving = false,
                        celebration = SavingsCelebration(goal.name, CurrencyAmount(goal.currencyCode, preview.newAmount))
                    )
                }
                else -> _events.send(SavingsContributionEvent.Close)
            }
        }
    }

    /** Toute modification passe par ici pour garder [SavingsContributionState.preview] à jour. */
    private fun updateState(transform: (SavingsContributionState) -> SavingsContributionState) {
        _state.update { current ->
            transform(current).let { it.copy(preview = previewOf(it), errorRes = null) }
        }
    }

    private fun previewOf(state: SavingsContributionState): ContributionPreview {
        val goal = state.goal ?: return ContributionPreview.Empty
        if (state.amountInput.isBlank()) return ContributionPreview.Empty
        val amount = Money.parseToMinorUnits(state.amountInput) ?: return ContributionPreview.InvalidAmount
        return SavingsContribution.preview(goal.currentAmount, goal.targetAmount, state.type, amount)
    }
}
