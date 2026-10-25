package com.naniger.arzikina.presentation.shop.stock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.domain.repository.ProductRepository
import com.naniger.arzikina.domain.repository.ShopRepository
import com.naniger.arzikina.domain.repository.StockRepository
import com.naniger.arzikina.domain.shop.Product
import com.naniger.arzikina.domain.shop.StockChangeError
import com.naniger.arzikina.domain.shop.StockException
import com.naniger.arzikina.domain.shop.StockMovementPolicy
import com.naniger.arzikina.domain.shop.StockMovementRequest
import com.naniger.arzikina.domain.shop.StockMovementType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StockMovementFormState(
    val type: StockMovementType,
    val product: Product? = null,
    val currencyCode: String = "",
    val errors: Set<StockChangeError> = emptySet(),
    val saving: Boolean = false,
    val loading: Boolean = true
)

sealed interface StockMovementFormEvent {
    data object Saved : StockMovementFormEvent
    data object Failed : StockMovementFormEvent
}

/**
 * Saisie d'un mouvement de stock manuel. Le type vient de l'argument de navigation (réapprovisionnement,
 * retour, perte, correction). Toutes les règles sont appliquées par le repository ; ce ViewModel traduit
 * la saisie et remonte les erreurs de champ.
 */
@HiltViewModel
class ShopStockMovementViewModel @Inject constructor(
    savedState: SavedStateHandle,
    products: ProductRepository,
    shops: ShopRepository,
    private val stock: StockRepository
) : ViewModel() {

    private val productId: Long = savedState.get<Long>(ARG_PRODUCT_ID) ?: 0L

    val type: StockMovementType = savedState.get<String>(ARG_TYPE)
        ?.let { name -> StockMovementPolicy.MANUAL_TYPES.firstOrNull { it.name == name } }
        ?: StockMovementType.RESTOCK

    private val errors = MutableStateFlow<Set<StockChangeError>>(emptySet())
    private val saving = MutableStateFlow(false)

    val uiState: StateFlow<StockMovementFormState> = combine(
        products.observeProduct(productId), shops.observeShop(), errors, saving
    ) { product, shop, formErrors, isSaving ->
        StockMovementFormState(
            type = type,
            product = product,
            currencyCode = shop?.currencyCode.orEmpty(),
            errors = formErrors,
            saving = isSaving,
            loading = false
        )
    }
        .catch { emit(StockMovementFormState(type = type, loading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StockMovementFormState(type = type))

    private val _events = MutableSharedFlow<StockMovementFormEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<StockMovementFormEvent> = _events.asSharedFlow()

    fun onSave(input: StockMovementFormInput) {
        if (!saving.compareAndSet(expect = false, update = true)) return
        when (val parsed = StockMovementFormParser.parse(input.copy(type = type))) {
            is StockMovementFormResult.Invalid -> {
                errors.value = parsed.errors
                saving.value = false
            }
            is StockMovementFormResult.Valid -> {
                errors.value = emptySet()
                viewModelScope.launch {
                    try {
                        if (type == StockMovementType.ADJUSTMENT) {
                            stock.adjustStockTo(productId, parsed.quantityMilli, parsed.reason.orEmpty())
                        } else {
                            stock.recordMovement(
                                StockMovementRequest(
                                    productId = productId,
                                    type = type,
                                    deltaMilli = StockMovementFormParser.signedDelta(type, parsed.quantityMilli),
                                    reason = parsed.reason,
                                    reference = parsed.reference,
                                    unitCostMinor = parsed.unitCostMinor,
                                    updatePurchasePrice = parsed.updatePurchasePrice
                                )
                            )
                        }
                        _events.tryEmit(StockMovementFormEvent.Saved)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: StockException.Rejected) {
                        errors.value = setOf(e.error)
                    } catch (e: Exception) {
                        _events.tryEmit(StockMovementFormEvent.Failed)
                    } finally {
                        saving.value = false
                    }
                }
            }
        }
    }

    companion object {
        const val ARG_PRODUCT_ID = "productId"
        const val ARG_TYPE = "type"
    }
}
