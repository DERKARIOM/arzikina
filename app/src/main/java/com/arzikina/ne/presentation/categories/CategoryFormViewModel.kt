package com.arzikina.ne.presentation.categories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.CategoryIcon
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.CategoryRepository
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
 * État du formulaire d'ajout/édition de catégorie.
 *
 * `Category.id == 0L` fait office de sentinelle "nouvelle catégorie" (même
 * convention que [Category.id] et [CategoryRepository.saveCategory]).
 */
data class CategoryFormState(
    val name: String = "",
    val icon: CategoryIcon = CategoryIcon.OTHER,
    val colorArgb: Long = 0xFF10B981L,
    val type: TransactionType = TransactionType.EXPENSE,
    val createdAt: Long? = null,
    val nameError: String? = null
)

sealed interface CategoryFormEvent {
    data object Saved : CategoryFormEvent
    data object Deleted : CategoryFormEvent

    /** Voir [CategoriesEvent.DeleteBlocked] : même contrainte `NO_ACTION`, même raisonnement,
     * juste déclenchée depuis ce second point d'entrée de suppression (le formulaire d'édition,
     * plutôt que la liste). */
    data object DeleteBlocked : CategoryFormEvent
}

@HiltViewModel
class CategoryFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val categoryId: Long = savedStateHandle.get<Long>(CATEGORY_ID_ARG) ?: 0L
    val isEditMode: Boolean = categoryId != 0L

    private val _formState = MutableStateFlow(CategoryFormState())
    val formState: StateFlow<CategoryFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<CategoryFormEvent>()
    val events: SharedFlow<CategoryFormEvent> = _events.asSharedFlow()

    init {
        if (isEditMode) {
            viewModelScope.launch {
                categoryRepository.getCategory(categoryId)?.let { category ->
                    _formState.update {
                        it.copy(
                            name = category.name,
                            icon = category.icon,
                            colorArgb = category.colorArgb,
                            type = category.type,
                            createdAt = category.createdAt
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _formState.update { it.copy(name = value, nameError = null) }
    }

    fun onIconChange(icon: CategoryIcon) {
        _formState.update { it.copy(icon = icon) }
    }

    fun onColorChange(colorArgb: Long) {
        _formState.update { it.copy(colorArgb = colorArgb) }
    }

    fun onTypeChange(type: TransactionType) {
        _formState.update { it.copy(type = type) }
    }

    fun save() {
        val state = _formState.value
        val trimmedName = state.name.trim()
        if (trimmedName.isEmpty()) {
            _formState.update { it.copy(nameError = "Le nom est obligatoire") }
            return
        }

        viewModelScope.launch {
            categoryRepository.saveCategory(
                Category(
                    id = categoryId,
                    name = trimmedName,
                    icon = state.icon,
                    colorArgb = state.colorArgb,
                    type = state.type,
                    createdAt = state.createdAt ?: System.currentTimeMillis()
                )
            )
            _events.emit(CategoryFormEvent.Saved)
        }
    }

    /** Voir la doc de [CategoryFormEvent.DeleteBlocked]/`CategoriesViewModel.deleteCategory` :
     * intercepte la contrainte `NO_ACTION` plutôt que de laisser planter l'app. */
    fun delete() {
        if (!isEditMode) return
        viewModelScope.launch {
            runCatching { categoryRepository.deleteCategory(categoryId) }
                .onSuccess { _events.emit(CategoryFormEvent.Deleted) }
                .onFailure { _events.emit(CategoryFormEvent.DeleteBlocked) }
        }
    }

    private companion object {
        const val CATEGORY_ID_ARG = "categoryId"
    }
}
