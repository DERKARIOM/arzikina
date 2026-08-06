package com.arzikina.ne.presentation.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.SavingsGoal
import com.arzikina.ne.domain.repository.SavingsGoalRepository
import com.arzikina.ne.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavingsGoalsViewModel @Inject constructor(
    private val savingsGoalRepository: SavingsGoalRepository
) : ViewModel() {

    val uiState: StateFlow<AppResult<List<SavingsGoal>>> = savingsGoalRepository.observeSavingsGoals()
        .map<List<SavingsGoal>, AppResult<List<SavingsGoal>>> { AppResult.Success(it) }
        .catch { throwable -> emit(AppResult.Error(throwable.message ?: "Erreur inconnue", throwable)) }
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

    fun addContribution(id: Long, amountMinor: Long) {
        viewModelScope.launch {
            savingsGoalRepository.addContribution(id, amountMinor)
        }
    }
}
