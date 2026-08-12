package com.arzikina.ne.presentation.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.CategoryRepository
import com.arzikina.ne.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Filtre affiché en haut de l'écran liste. */
enum class CategoryFilter {
    ALL,
    INCOME,
    EXPENSE
}

sealed interface CategoriesEvent {
    /** `categoryId` en `NO_ACTION` (voir `TransactionEntity`/`RecurringTransactionEntity`) : la
     * base refuse la suppression d'une catégorie encore référencée par une transaction ou une
     * règle récurrente plutôt que de la supprimer en silence — voir [CategoriesViewModel.deleteCategory]. */
    data object DeleteBlocked : CategoriesEvent
}

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(CategoryFilter.ALL)
    val filter: StateFlow<CategoryFilter> = _filter.asStateFlow()

    private val _events = MutableSharedFlow<CategoriesEvent>()
    val events: SharedFlow<CategoriesEvent> = _events.asSharedFlow()

    val uiState: StateFlow<AppResult<List<Category>>> = _filter
        .flatMapLatest { filter ->
            when (filter) {
                CategoryFilter.ALL -> categoryRepository.observeCategories()
                CategoryFilter.INCOME -> categoryRepository.observeCategoriesByType(TransactionType.INCOME)
                CategoryFilter.EXPENSE -> categoryRepository.observeCategoriesByType(TransactionType.EXPENSE)
            }
        }
        .map<List<Category>, AppResult<List<Category>>> { AppResult.Success(it) }
        .catch { throwable -> emit(AppResult.Error(throwable.message ?: "Erreur inconnue", throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )

    fun onFilterChange(newFilter: CategoryFilter) {
        _filter.value = newFilter
    }

    /**
     * `categoryRepository.deleteCategory` peut lever une exception SQLite (contrainte de clé
     * étrangère `NO_ACTION`, voir la doc de [CategoriesEvent.DeleteBlocked]) si cette catégorie est
     * encore utilisée par une transaction ou une règle récurrente — interceptée ici plutôt que de
     * laisser planter l'app (voir la doc de `TransactionEntity.categoryId`, qui demandait déjà
     * explicitement cette interception côté couches supérieures, jamais faite jusqu'ici).
     */
    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            runCatching { categoryRepository.deleteCategory(id) }
                .onFailure { _events.emit(CategoriesEvent.DeleteBlocked) }
        }
    }
}
