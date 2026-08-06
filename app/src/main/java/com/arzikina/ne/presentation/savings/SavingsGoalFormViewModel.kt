package com.arzikina.ne.presentation.savings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.SavingsGoal
import com.arzikina.ne.domain.repository.SavingsGoalRepository
import com.arzikina.ne.util.Constants
import com.arzikina.ne.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * État du formulaire d'ajout/édition d'un objectif d'épargne.
 *
 * `SavingsGoal.id == 0L` fait office de sentinelle "nouvel objectif" (même
 * convention que [SavingsGoal.id]). `hasDeadline` détermine si [deadlineMillis]
 * est réellement utilisé à l'enregistrement (voir [save]) : l'échéance est
 * optionnelle dans le domaine.
 */
data class SavingsGoalFormState(
    val name: String = "",
    val targetInput: String = "",
    val currentInput: String = "0",
    val currencyCode: String = Constants.DEFAULT_CURRENCY_CODE,
    val hasDeadline: Boolean = false,
    val deadlineMillis: Long = System.currentTimeMillis(),
    val createdAt: Long? = null,
    val nameError: String? = null,
    val targetError: String? = null,
    val currentError: String? = null
)

sealed interface SavingsGoalFormEvent {
    data object Saved : SavingsGoalFormEvent
    data object Deleted : SavingsGoalFormEvent
}

@HiltViewModel
class SavingsGoalFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val savingsGoalRepository: SavingsGoalRepository
) : ViewModel() {

    private val goalId: Long = savedStateHandle.get<Long>(SAVINGS_GOAL_ID_ARG) ?: 0L
    val isEditMode: Boolean = goalId != 0L

    private val _formState = MutableStateFlow(SavingsGoalFormState())
    val formState: StateFlow<SavingsGoalFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<SavingsGoalFormEvent>()
    val events: SharedFlow<SavingsGoalFormEvent> = _events.asSharedFlow()

    init {
        if (isEditMode) {
            viewModelScope.launch {
                savingsGoalRepository.getSavingsGoal(goalId)?.let { goal ->
                    _formState.update {
                        it.copy(
                            name = goal.name,
                            targetInput = Money.formatMajorUnits(goal.targetAmount),
                            currentInput = Money.formatMajorUnits(goal.currentAmount),
                            currencyCode = goal.currencyCode,
                            hasDeadline = goal.deadline != null,
                            deadlineMillis = goal.deadline ?: System.currentTimeMillis(),
                            createdAt = goal.createdAt
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _formState.update { it.copy(name = value, nameError = null) }
    }

    fun onTargetChange(value: String) {
        _formState.update { it.copy(targetInput = value, targetError = null) }
    }

    fun onCurrentChange(value: String) {
        _formState.update { it.copy(currentInput = value, currentError = null) }
    }

    fun onCurrencyChange(code: String) {
        _formState.update { it.copy(currencyCode = code) }
    }

    fun onHasDeadlineChange(hasDeadline: Boolean) {
        _formState.update { it.copy(hasDeadline = hasDeadline) }
    }

    /** L'échéance est une simple date, sans heure : toujours ramenée à minuit. */
    fun onDeadlineChange(date: LocalDate) {
        _formState.update {
            it.copy(deadlineMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
    }

    fun save() {
        val state = _formState.value
        val trimmedName = state.name.trim()
        if (trimmedName.isEmpty()) {
            _formState.update { it.copy(nameError = "Le nom est obligatoire") }
            return
        }

        val targetMinor = Money.parseToMinorUnits(state.targetInput)
        if (targetMinor == null || targetMinor <= 0L) {
            _formState.update { it.copy(targetError = "Montant cible invalide") }
            return
        }

        val currentMinor = Money.parseToMinorUnits(state.currentInput)
        if (currentMinor == null) {
            _formState.update { it.copy(currentError = "Montant invalide") }
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

    private companion object {
        const val SAVINGS_GOAL_ID_ARG = "savingsGoalId"
    }
}
