package com.naniger.arzikina.presentation.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.domain.repository.SavingsGoalRepository
import com.naniger.arzikina.domain.repository.UserPreferencesRepository
import com.naniger.arzikina.util.AppResult
import com.naniger.arzikina.util.technicalMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Écran « Mes objectifs d'épargne » : lignes prêtes à afficher (voir [buildSavingsGoalsRows]).
 * La devise principale (Paramètres) sert à la carte de synthèse.
 */
@HiltViewModel
class SavingsGoalsViewModel @Inject constructor(
    private val savingsGoalRepository: SavingsGoalRepository,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<AppResult<List<SavingsGoalsRow>>> = combine(
        savingsGoalRepository.observeSavingsGoals(),
        userPreferencesRepository.observePreferences().map { it.currencyCode }.distinctUntilChanged()
    ) { goals, mainCurrencyCode ->
        buildSavingsGoalsRows(goals, mainCurrencyCode, LocalDate.now())
    }
        .map<List<SavingsGoalsRow>, AppResult<List<SavingsGoalsRow>>> { AppResult.Success(it) }
        .catch { throwable -> emit(AppResult.Error(throwable.technicalMessage(), throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )

    fun deleteSavingsGoal(id: Long) {
        viewModelScope.launch {
            savingsGoalRepository.deleteSavingsGoal(id)
        }
    }
}
