package com.arzikina.ne.presentation.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.CategoryRepository
import com.arzikina.ne.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(CategoryFilter.ALL)
    val filter: StateFlow<CategoryFilter> = _filter.asStateFlow()

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

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            categoryRepository.deleteCategory(id)
        }
    }
}
