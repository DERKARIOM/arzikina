package com.arzikina.ne.presentation.utilities.financialplan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.FinancialPlan
import com.arzikina.ne.domain.model.FinancialPlanIcon
import com.arzikina.ne.domain.model.PlanPeriodType
import com.arzikina.ne.domain.model.PlanStatus
import com.arzikina.ne.domain.repository.FinancialPlanRepository
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
import javax.inject.Inject

/**
 * État du formulaire d'ajout/édition d'une planification.
 *
 * Cette première étape ne couvre que nom + montant disponible + icône + couleur (voir cahier des
 * charges, section 3) — description, objectif financier et période seront ajoutés par une étape
 * ultérieure du plan (voir [FinancialPlan.description]/[FinancialPlan.targetAmount]/
 * [FinancialPlan.periodType], déjà présents dans le modèle de données depuis l'Étape 2 mais pas
 * encore éditables ici).
 *
 * `FinancialPlan.id == 0L` fait office de sentinelle "nouvelle planification" (même convention que
 * [com.arzikina.ne.domain.model.Budget.id]).
 */
data class FinancialPlanFormState(
    val nameInput: String = "",
    val availableAmountInput: String = "",
    val icon: FinancialPlanIcon = FinancialPlanIcon.WALLET,
    val colorArgb: Long = DEFAULT_COLOR_ARGB,
    val nameError: String? = null,
    val availableAmountError: String? = null
) {
    companion object {
        /** Reprend la première couleur de [com.arzikina.ne.presentation.components.ColorPalette]
         * (jamais 0xFF42B998, le défaut du modèle de domaine [FinancialPlan.colorArgb]) : voir la
         * doc de `ColorPalette` — cette liste ne doit pas grandir pour ne pas perturber
         * [com.arzikina.ne.presentation.utilities.loans.personAvatarColorArgb]. */
        const val DEFAULT_COLOR_ARGB = 0xFF10B981L
    }
}

sealed interface FinancialPlanFormEvent {
    data object Saved : FinancialPlanFormEvent
    data object Deleted : FinancialPlanFormEvent
}

@HiltViewModel
class FinancialPlanFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val financialPlanRepository: FinancialPlanRepository
) : ViewModel() {

    private val planId: Long = savedStateHandle.get<Long>(PLAN_ID_ARG) ?: 0L
    val isEditMode: Boolean = planId != 0L

    private val _formState = MutableStateFlow(FinancialPlanFormState())
    val formState: StateFlow<FinancialPlanFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<FinancialPlanFormEvent>()
    val events: SharedFlow<FinancialPlanFormEvent> = _events.asSharedFlow()

    private var existingPlan: FinancialPlan? = null

    init {
        if (isEditMode) {
            viewModelScope.launch {
                financialPlanRepository.getPlan(planId)?.let { plan ->
                    existingPlan = plan
                    _formState.update {
                        it.copy(
                            nameInput = plan.name,
                            availableAmountInput = Money.formatMajorUnits(plan.availableAmount),
                            icon = plan.icon,
                            colorArgb = plan.colorArgb
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _formState.update { it.copy(nameInput = value, nameError = null) }
    }

    fun onAvailableAmountChange(value: String) {
        _formState.update { it.copy(availableAmountInput = value, availableAmountError = null) }
    }

    fun onIconChange(icon: FinancialPlanIcon) {
        _formState.update { it.copy(icon = icon) }
    }

    fun onColorChange(colorArgb: Long) {
        _formState.update { it.copy(colorArgb = colorArgb) }
    }

    fun save() {
        val state = _formState.value

        if (state.nameInput.isBlank()) {
            _formState.update { it.copy(nameError = "Nom requis") }
            return
        }
        val availableAmountMinor = Money.parseToMinorUnits(state.availableAmountInput)
        if (availableAmountMinor == null || availableAmountMinor <= 0L) {
            _formState.update { it.copy(availableAmountError = "Montant invalide") }
            return
        }

        viewModelScope.launch {
            val current = existingPlan
            financialPlanRepository.savePlan(
                FinancialPlan(
                    id = planId,
                    name = state.nameInput.trim(),
                    description = current?.description,
                    availableAmount = availableAmountMinor,
                    targetAmount = current?.targetAmount,
                    periodType = current?.periodType ?: PlanPeriodType.NONE,
                    startDate = current?.startDate,
                    endDate = current?.endDate,
                    icon = state.icon,
                    colorArgb = state.colorArgb,
                    status = current?.status ?: PlanStatus.ACTIVE,
                    createdAt = current?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            _events.emit(FinancialPlanFormEvent.Saved)
        }
    }

    fun delete() {
        if (!isEditMode) return
        viewModelScope.launch {
            financialPlanRepository.deletePlan(planId)
            _events.emit(FinancialPlanFormEvent.Deleted)
        }
    }

    private companion object {
        const val PLAN_ID_ARG = "planId"
    }
}
