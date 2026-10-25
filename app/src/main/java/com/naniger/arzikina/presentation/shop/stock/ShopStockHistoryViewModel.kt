package com.naniger.arzikina.presentation.shop.stock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.domain.repository.ProductRepository
import com.naniger.arzikina.domain.repository.ShopRepository
import com.naniger.arzikina.domain.repository.StockRepository
import com.naniger.arzikina.domain.shop.Product
import com.naniger.arzikina.domain.shop.StockMovement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface StockHistoryUiState {
    data object Loading : StockHistoryUiState
    data object Error : StockHistoryUiState

    /** [hasMore] : la page est pleine, il peut exister des mouvements plus anciens. */
    data class Loaded(
        val product: Product?,
        val items: List<StockMovement>,
        val currencyCode: String,
        val hasMore: Boolean
    ) : StockHistoryUiState
}

/**
 * Historique des mouvements d'un produit, par pages de [PAGE_SIZE] : la limite grandit quand l'utilisateur
 * atteint le bas de la liste (la requête reste bornée, jamais tout l'historique en mémoire d'un coup).
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ShopStockHistoryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val stock: StockRepository,
    private val products: ProductRepository,
    private val shops: ShopRepository
) : ViewModel() {

    private val productId: Long = savedState.get<Long>(ARG_PRODUCT_ID) ?: 0L
    private val limit = MutableStateFlow(PAGE_SIZE)

    val uiState: StateFlow<StockHistoryUiState> = limit
        .flatMapLatest { l ->
            combine(
                stock.observeMovements(productId, l),
                products.observeProduct(productId),
                shops.observeShop()
            ) { items, product, shop ->
                StockHistoryUiState.Loaded(product, items, shop?.currencyCode.orEmpty(), hasMore = items.size >= l)
                    as StockHistoryUiState
            }
        }
        .catch { emit(StockHistoryUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StockHistoryUiState.Loading)

    /** Charge la page suivante si la dernière est pleine. */
    fun onEndReached() {
        val state = uiState.value
        if (state is StockHistoryUiState.Loaded && state.hasMore) limit.value = limit.value + PAGE_SIZE
    }

    companion object {
        const val ARG_PRODUCT_ID = "productId"
        const val PAGE_SIZE = 50
    }
}
