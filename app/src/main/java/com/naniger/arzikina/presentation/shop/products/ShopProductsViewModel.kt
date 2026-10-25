package com.naniger.arzikina.presentation.shop.products

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.domain.repository.ProductRepository
import com.naniger.arzikina.domain.repository.ShopRepository
import com.naniger.arzikina.domain.shop.ProductAvailability
import com.naniger.arzikina.domain.shop.ProductCategory
import com.naniger.arzikina.domain.shop.ProductFilter
import com.naniger.arzikina.domain.shop.ProductSort
import com.naniger.arzikina.domain.shop.ProductSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

sealed interface ProductListUiState {
    data object Loading : ProductListUiState
    data object Error : ProductListUiState

    /** [filtersActive] distingue « aucun produit du tout » de « aucun résultat pour ces critères ». */
    data class Loaded(
        val items: List<ProductSummary>,
        val currencyCode: String,
        val filtersActive: Boolean
    ) : ProductListUiState
}

/**
 * Liste des produits : recherche, disponibilité, catégorie, tri et archivés. Le filtrage et le tri sont
 * exécutés par SQLite (voir `ProductDao.observeSummaries`) ; la saisie est temporisée pour ne pas relancer
 * une requête à chaque caractère. Le filtre de disponibilité initial vient de l'argument de navigation
 * `availability` (ex. raccourci « Alertes de stock » du tableau de bord).
 */
@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ShopProductsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val products: ProductRepository,
    shops: ShopRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(
        ProductFilter(availability = ProductAvailability.fromCode(savedStateHandle.get<Int>(ARG_AVAILABILITY) ?: 0))
    )
    val filter: StateFlow<ProductFilter> = _filter.asStateFlow()

    val categories: StateFlow<List<ProductCategory>> = products.observeCategories()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<ProductListUiState> = _filter
        .debounce { if (it.query.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .flatMapLatest { f ->
            combine(products.observeProducts(f), shops.observeShop()) { items, shop ->
                ProductListUiState.Loaded(items, shop?.currencyCode.orEmpty(), f.isActive) as ProductListUiState
            }.catch { emit(ProductListUiState.Error) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductListUiState.Loading)

    fun onQueryChange(query: String) = _filter.update { it.copy(query = query) }
    fun onAvailabilityChange(availability: ProductAvailability) = _filter.update { it.copy(availability = availability) }
    fun onSortChange(sort: ProductSort) = _filter.update { it.copy(sort = sort) }
    fun onCategoryChange(categoryId: Long?) = _filter.update { it.copy(categoryId = categoryId) }
    fun onArchivedChange(archived: Boolean) = _filter.update { it.copy(archived = archived) }

    companion object {
        const val ARG_AVAILABILITY = "availability"
        private const val SEARCH_DEBOUNCE_MS = 250L
    }
}
