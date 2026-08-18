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
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * État du formulaire d'ajout/édition d'une planification.
 *
 * Nom + montant disponible + icône + couleur depuis l'Étape 3, période + objectif financier depuis
 * l'Étape 8 (voir [FinancialPlan.targetAmount]/[FinancialPlan.periodType]/[FinancialPlan.startDate]/
 * [FinancialPlan.endDate], déjà présents dans le modèle de données depuis l'Étape 2 mais pas encore
 * éditables jusqu'ici). [FinancialPlan.description] reste hors périmètre : aucun champ dédié dans
 * l'UI pour l'instant, préservé tel quel depuis [existingPlan] à l'enregistrement (voir [save]).
 *
 * [periodType]/[startDate]/[endDate] : [startDate]/[endDate] n'ont de sens que si [periodType] est
 * différent de [PlanPeriodType.NONE] (voir la doc de [FinancialPlan.periodType]) —
 * [FinancialPlanFormFragment] masque alors les deux champs de date plutôt que de les désactiver,
 * même principe que `RecurringTransactionFormState.hasEndDate`/`endDateCard`.
 *
 * [targetAmountInput] : objectif financier OPTIONNEL — vide = aucun objectif (`null`), même
 * convention que `FinancialPlanItemFormState.descriptionInput` pour les champs facultatifs.
 *
 * `FinancialPlan.id == 0L` fait office de sentinelle "nouvelle planification" (même convention que
 * [com.arzikina.ne.domain.model.Budget.id]).
 */
data class FinancialPlanFormState(
    val nameInput: String = "",
    val availableAmountInput: String = "",
    val targetAmountInput: String = "",
    val periodType: PlanPeriodType = PlanPeriodType.NONE,
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long = System.currentTimeMillis(),
    val icon: FinancialPlanIcon = FinancialPlanIcon.WALLET,
    val colorArgb: Long = DEFAULT_COLOR_ARGB,
    val nameError: String? = null,
    val availableAmountError: String? = null,
    val targetAmountError: String? = null,
    val dateError: String? = null
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
                            targetAmountInput = plan.targetAmount?.let { amount -> Money.formatMajorUnits(amount) }.orEmpty(),
                            periodType = plan.periodType,
                            startDate = plan.startDate ?: System.currentTimeMillis(),
                            endDate = plan.endDate ?: plan.startDate ?: System.currentTimeMillis(),
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

    fun onTargetAmountChange(value: String) {
        _formState.update { it.copy(targetAmountInput = value, targetAmountError = null) }
    }

    fun onPeriodTypeChange(periodType: PlanPeriodType) {
        _formState.update { it.copy(periodType = periodType, dateError = null) }
    }

    fun onStartDateChange(millis: Long) {
        _formState.update { it.copy(startDate = millis, dateError = null) }
    }

    fun onEndDateChange(millis: Long) {
        _formState.update { it.copy(endDate = millis, dateError = null) }
    }

    fun onIconChange(icon: FinancialPlanIcon) {
        _formState.update { it.copy(icon = icon) }
    }

    fun onColorChange(colorArgb: Long) {
        _formState.update { it.copy(colorArgb = colorArgb) }
    }

    fun save() {
        val state = _formState.value

        val nameError = if (state.nameInput.isBlank()) "Nom requis" else null
        val availableAmountMinor = Money.parseToMinorUnits(state.availableAmountInput)
        val availableAmountError = if (availableAmountMinor == null || availableAmountMinor <= 0L) "Montant invalide" else null

        // Objectif financier OPTIONNEL (voir la doc de FinancialPlanFormState.targetAmountInput) :
        // une saisie vide n'est jamais une erreur, seule une saisie NON VIDE mais invalide l'est.
        val targetAmountMinor = if (state.targetAmountInput.isBlank()) null else Money.parseToMinorUnits(state.targetAmountInput)
        val targetAmountError = if (state.targetAmountInput.isNotBlank() && (targetAmountMinor == null || targetAmountMinor <= 0L)) {
            "Montant invalide"
        } else {
            null
        }

        // Dates ignorées si periodType == NONE (voir la doc de FinancialPlan.periodType) : pas de
        // validation à faire dans ce cas, même principe que
        // RecurringTransactionFormViewModel.save/endDateValid quand hasEndDate == false.
        val dateError = if (state.periodType != PlanPeriodType.NONE && isBeforeDay(state.endDate, state.startDate)) {
            "La date de fin doit être après la date de début"
        } else {
            null
        }

        if (nameError != null || availableAmountError != null || targetAmountError != null || dateError != null) {
            _formState.update {
                it.copy(
                    nameError = nameError,
                    availableAmountError = availableAmountError,
                    targetAmountError = targetAmountError,
                    dateError = dateError
                )
            }
            return
        }
        checkNotNull(availableAmountMinor)

        viewModelScope.launch {
            val current = existingPlan
            financialPlanRepository.savePlan(
                FinancialPlan(
                    id = planId,
                    name = state.nameInput.trim(),
                    description = current?.description,
                    availableAmount = availableAmountMinor,
                    targetAmount = targetAmountMinor,
                    periodType = state.periodType,
                    startDate = state.startDate.takeIf { state.periodType != PlanPeriodType.NONE },
                    endDate = state.endDate.takeIf { state.periodType != PlanPeriodType.NONE },
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

    /** Voir `RecurringTransactionFormViewModel.isAfterDay`/`isSameDay` pour le même raisonnement
     * (comparaison au jour près, pas à la milliseconde près). */
    private fun isBeforeDay(date: Long, reference: Long): Boolean = toLocalDate(date).isBefore(toLocalDate(reference))
    private fun toLocalDate(epochMillis: Long) = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

    private companion object {
        const val PLAN_ID_ARG = "planId"
    }
}
