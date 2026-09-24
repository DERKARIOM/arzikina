package com.naniger.arzikina.presentation.savings

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.SavingsGoal
import com.naniger.arzikina.domain.repository.SavingsGoalRepository
import com.naniger.arzikina.domain.repository.UserPreferencesRepository
import com.naniger.arzikina.util.Constants
import com.naniger.arzikina.util.DatePeriods
import com.naniger.arzikina.util.Money
import com.naniger.arzikina.util.SavingsGoalProgress
import com.naniger.arzikina.util.SavingsSuggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * État du formulaire d'ajout/édition d'un objectif d'épargne. Les montants restent du texte saisi
 * (formaté en direct par `MoneyInputFormatter`) jusqu'à l'enregistrement.
 *
 * [suggestion] : « ≈ X par mois » recalculé à chaque saisie (voir [SavingsGoalProgress.suggestionOf]),
 * `null` sans échéance ou avec des montants encore invalides.
 */
data class SavingsGoalFormState(
    val name: String = "",
    val targetInput: String = "",
    val currentInput: String = "",
    val currencyCode: String = Constants.DEFAULT_CURRENCY_CODE,
    val hasDeadline: Boolean = false,
    val deadlineMillis: Long = defaultDeadlineMillis(),
    val createdAt: Long? = null,
    val suggestion: SavingsSuggestion? = null,
    @StringRes val nameError: Int? = null,
    @StringRes val targetError: Int? = null,
    @StringRes val currentError: Int? = null
) {
    private companion object {
        /** Échéance proposée quand l'utilisateur active l'interrupteur : dans 3 mois. */
        fun defaultDeadlineMillis(): Long = DatePeriods.toEpochMillis(LocalDate.now().plusMonths(3))
    }
}

sealed interface SavingsGoalFormEvent {
    data object Saved : SavingsGoalFormEvent
    data object Deleted : SavingsGoalFormEvent
}

@HiltViewModel
class SavingsGoalFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val goalId: Long = SavingsGoalFormFragmentArgs.fromSavedStateHandle(savedStateHandle).savingsGoalId
    val isEditMode: Boolean = goalId != 0L

    private val _formState = MutableStateFlow(SavingsGoalFormState())
    val formState: StateFlow<SavingsGoalFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<SavingsGoalFormEvent>()
    val events: SharedFlow<SavingsGoalFormEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            if (isEditMode) {
                savingsGoalRepository.getSavingsGoal(goalId)?.let { goal ->
                    updateState {
                        it.copy(
                            name = goal.name,
                            targetInput = Money.formatForInput(goal.targetAmount),
                            currentInput = Money.formatForInput(goal.currentAmount),
                            currencyCode = goal.currencyCode,
                            hasDeadline = goal.deadline != null,
                            deadlineMillis = goal.deadline ?: it.deadlineMillis,
                            createdAt = goal.createdAt
                        )
                    }
                }
            } else {
                // Nouvel objectif : la devise principale choisie dans Paramètres, pas une valeur figée.
                val mainCurrency = userPreferencesRepository.observePreferences().first().currencyCode
                updateState { it.copy(currencyCode = mainCurrency) }
            }
        }
    }

    fun onNameChange(value: String) = updateState { it.copy(name = value, nameError = null) }

    fun onTargetChange(value: String) = updateState { it.copy(targetInput = value, targetError = null) }

    fun onCurrentChange(value: String) = updateState { it.copy(currentInput = value, currentError = null) }

    fun onCurrencyChange(code: String) = updateState { it.copy(currencyCode = code) }

    fun onHasDeadlineChange(hasDeadline: Boolean) = updateState { it.copy(hasDeadline = hasDeadline) }

    /** [epochMillis] : début de journée, heure locale (voir le sélecteur de date du Fragment). */
    fun onDeadlineChange(epochMillis: Long) = updateState { it.copy(deadlineMillis = epochMillis) }

    fun save() {
        val state = _formState.value
        val trimmedName = state.name.trim()
        val targetMinor = Money.parseToMinorUnits(state.targetInput)?.takeIf { it > 0L }
        val currentMinor = parseCurrent(state.currentInput)

        if (trimmedName.isEmpty() || targetMinor == null || currentMinor == null) {
            _formState.update {
                it.copy(
                    nameError = if (trimmedName.isEmpty()) R.string.error_name_required else null,
                    targetError = if (targetMinor == null) R.string.error_invalid_target_amount else null,
                    currentError = if (currentMinor == null) R.string.error_invalid_amount else null
                )
            }
            return
        }

        viewModelScope.launch {
            savingsGoalRepository.saveSavingsGoal(
                SavingsGoal(
                    id = goalId,
                    name = trimmedName,
                    targetAmount = targetMinor,
                    currentAmount = currentMinor,
                    currencyCode = state.currencyCode,
                    deadline = if (state.hasDeadline) state.deadlineMillis else null,
                    createdAt = state.createdAt ?: System.currentTimeMillis()
                )
            )
            _events.emit(SavingsGoalFormEvent.Saved)
        }
    }

    fun delete() {
        if (!isEditMode) return
        viewModelScope.launch {
            savingsGoalRepository.deleteSavingsGoal(goalId)
            _events.emit(SavingsGoalFormEvent.Deleted)
        }
    }

    /** Toute mise à jour passe par ici pour que [SavingsGoalFormState.suggestion] reste à jour. */
    private fun updateState(transform: (SavingsGoalFormState) -> SavingsGoalFormState) {
        _formState.update { current -> transform(current).let { it.copy(suggestion = suggestionOf(it)) } }
    }

    private fun suggestionOf(state: SavingsGoalFormState): SavingsSuggestion? {
        if (!state.hasDeadline) return null
        val target = Money.parseToMinorUnits(state.targetInput)?.takeIf { it > 0L } ?: return null
        val current = parseCurrent(state.currentInput) ?: return null
        return SavingsGoalProgress.suggestionOf(
            currentAmount = current,
            targetAmount = target,
            deadline = DatePeriods.toLocalDate(state.deadlineMillis),
            today = LocalDate.now()
        )
    }

    /** Champ « déjà épargné » laissé vide = 0 ; sinon un montant positif valide (`null` = invalide). */
    private fun parseCurrent(input: String): Long? =
        if (input.isBlank()) 0L else Money.parseToMinorUnits(input)
}
