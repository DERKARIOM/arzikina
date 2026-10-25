package com.naniger.arzikina.presentation.shop.detail

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.repository.ProductRepository
import com.naniger.arzikina.domain.repository.ShopRepository
import com.naniger.arzikina.domain.shop.Product
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProductDetailUiState {
    data object Loading : ProductDetailUiState
    data object NotFound : ProductDetailUiState
    data class Loaded(val product: Product, val categoryName: String?, val currencyCode: String) : ProductDetailUiState
}

sealed interface ProductDetailEvent {
    data class Message(@StringRes val messageRes: Int) : ProductDetailEvent
}

/** Fiche d'un produit : lecture en direct (le stock se met à jour dès qu'un mouvement est enregistré). */
@HiltViewModel
class ShopProductDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val products: ProductRepository,
    shops: ShopRepository
) : ViewModel() {

    val productId: Long = savedStateHandle.get<Long>(ARG_PRODUCT_ID) ?: 0L

    val uiState: StateFlow<ProductDetailUiState> = combine(
        products.observeProduct(productId),
        products.observeCategories(),
        shops.observeShop()
    ) { product, categories, shop ->
        if (product == null) {
            ProductDetailUiState.NotFound
        } else {
            ProductDetailUiState.Loaded(
                product = product,
                categoryName = categories.firstOrNull { it.id == product.categoryId }?.name,
                currencyCode = shop?.currencyCode.orEmpty()
            )
        }
    }
        .catch { emit(ProductDetailUiState.NotFound) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductDetailUiState.Loading)

    private val _events = MutableSharedFlow<ProductDetailEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ProductDetailEvent> = _events.asSharedFlow()

    /** Archive ou restaure le produit (jamais de suppression : ses ventes passées le référencent). */
    fun onSetArchived(archived: Boolean) {
        viewModelScope.launch {
            try {
                products.setArchived(productId, archived)
                _events.tryEmit(
                    ProductDetailEvent.Message(
                        if (archived) R.string.shop_detail_archived_done else R.string.shop_detail_restored_done
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.tryEmit(ProductDetailEvent.Message(R.string.shop_detail_action_failed))
            }
        }
    }

    companion object {
        const val ARG_PRODUCT_ID = "productId"
    }
}
