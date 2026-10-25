package com.naniger.arzikina.presentation.shop.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.domain.repository.ProductRepository
import com.naniger.arzikina.domain.repository.ShopRepository
import com.naniger.arzikina.domain.shop.Product
import com.naniger.arzikina.domain.shop.ProductCategory
import com.naniger.arzikina.domain.shop.ProductError
import com.naniger.arzikina.domain.shop.ProductException
import com.naniger.arzikina.domain.shop.ProductImageChange
import com.naniger.arzikina.domain.shop.ProductImageException
import com.naniger.arzikina.domain.shop.ProductQuantityFormat
import com.naniger.arzikina.domain.shop.ProductUnit
import com.naniger.arzikina.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Ce que le formulaire affiche en dehors des champs texte (qui conservent leur propre état). */
data class ProductFormUiState(
    val isEdit: Boolean,
    val currencyCode: String = "",
    val categories: List<ProductCategory> = emptyList(),
    val categoryId: Long? = null,
    val unit: ProductUnit = ProductUnit.PIECE,
    val expiryDate: Long? = null,
    val errors: Set<ProductError> = emptySet(),
    val saving: Boolean = false,
    val image: ProductFormImage = ProductFormImage.None
)

/** Photo affichée dans le formulaire : rien, image choisie (pas encore enregistrée) ou photo déjà enregistrée. */
sealed interface ProductFormImage {
    data object None : ProductFormImage
    data class Picked(val uri: String) : ProductFormImage
    data class Stored(val file: File) : ProductFormImage
}

sealed interface ProductFormEvent {
    data object Saved : ProductFormEvent
    data object SaveFailed : ProductFormEvent
    data object NotFound : ProductFormEvent
    data class DuplicateBarcode(val existingProductId: Long) : ProductFormEvent
    data class DuplicateSku(val existingProductId: Long) : ProductFormEvent
    data class ImageFailed(val reason: ProductImageException.Reason) : ProductFormEvent
}

/**
 * Formulaire produit (création si `productId == 0`, modification sinon). La catégorie, l'unité et la
 * date de péremption — qui ne sont pas des champs texte — vivent dans [SavedStateHandle] pour survivre à
 * la rotation et à la mort du processus ; les champs texte restaurent eux-mêmes leur contenu.
 */
@HiltViewModel
class ShopProductFormViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val products: ProductRepository,
    shops: ShopRepository
) : ViewModel() {

    private val productId: Long = savedState.get<Long>(ARG_PRODUCT_ID) ?: 0L
    val isEdit: Boolean = productId != 0L

    private val categoryId = savedState.getStateFlow<Long?>(KEY_CATEGORY, null)
    private val unitName = savedState.getStateFlow(KEY_UNIT, ProductUnit.PIECE.name)
    private val expiry = savedState.getStateFlow<Long?>(KEY_EXPIRY, null)
    private val errors = MutableStateFlow<Set<ProductError>>(emptySet())
    private val saving = MutableStateFlow(false)

    // Photo : choix en attente (survit à la rotation et à la mort du processus), retrait demandé, photo déjà enregistrée.
    private val pendingImageUri = savedState.getStateFlow<String?>(KEY_IMAGE_URI, null)
    private val imageRemoved = savedState.getStateFlow(KEY_IMAGE_REMOVED, false)
    private val storedImage = MutableStateFlow<File?>(null)

    private val image = combine(pendingImageUri, imageRemoved, storedImage) { picked, removed, stored ->
        when {
            picked != null -> ProductFormImage.Picked(picked)
            removed || stored == null -> ProductFormImage.None
            else -> ProductFormImage.Stored(stored)
        }
    }

    private val categories = products.observeCategories().catch { emit(emptyList()) }

    private val selection = combine(categoryId, unitName, expiry) { category, unit, date ->
        Selection(category, ProductUnit.fromStored(unit), date)
    }
    private val progress = combine(errors, saving) { e, s -> e to s }

    val uiState: StateFlow<ProductFormUiState> = combine(
        selection, progress, categories, shops.observeShop(), image
    ) { sel, (formErrors, isSaving), cats, shop, photo ->
        ProductFormUiState(
            isEdit = isEdit,
            currencyCode = shop?.currencyCode.orEmpty(),
            categories = cats,
            categoryId = sel.categoryId,
            unit = sel.unit,
            expiryDate = sel.expiry,
            errors = formErrors,
            saving = isSaving,
            image = photo
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductFormUiState(isEdit = isEdit))

    private data class Selection(val categoryId: Long?, val unit: ProductUnit, val expiry: Long?)

    private val _events = MutableSharedFlow<ProductFormEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ProductFormEvent> = _events.asSharedFlow()

    private val _prefill = MutableStateFlow<ProductFormInput?>(null)

    /** Saisie à pré-remplir en modification (une seule fois : voir [consumePrefill]). */
    val prefill: StateFlow<ProductFormInput?> = _prefill.asStateFlow()

    init {
        if (isEdit) loadProduct()
    }

    private fun loadProduct() {
        viewModelScope.launch {
            val product = try {
                products.getProduct(productId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (product == null) {
                _events.tryEmit(ProductFormEvent.NotFound)
            } else {
                // Après une mort du processus, l'état conservé (choix de l'utilisateur) l'emporte sur la base.
                if (!alreadyPrefilled()) {
                    savedState[KEY_UNIT] = product.unit.name
                    savedState[KEY_CATEGORY] = product.categoryId
                    savedState[KEY_EXPIRY] = product.expiryDate
                }
                storedImage.value = product.imageFile
                _prefill.value = product.toInput()
            }
        }
    }

    /**
     * Retourne la saisie à pré-remplir, UNE seule fois — y compris après rotation ou mort du processus
     * (drapeau dans [SavedStateHandle]) : les champs restaurent ensuite eux-mêmes ce que l'utilisateur a tapé.
     */
    fun consumePrefill(): ProductFormInput? {
        if (alreadyPrefilled()) return null
        val input = _prefill.value ?: return null
        savedState[KEY_PREFILLED] = true
        return input
    }

    private fun alreadyPrefilled(): Boolean = savedState.get<Boolean>(KEY_PREFILLED) == true

    fun onCategorySelected(id: Long?) { savedState[KEY_CATEGORY] = id }

    fun onUnitSelected(unit: ProductUnit) { savedState[KEY_UNIT] = unit.name }

    fun onExpirySelected(millis: Long?) { savedState[KEY_EXPIRY] = millis }

    /** Photo choisie (galerie ou appareil photo) : traitée et enregistrée seulement à « Enregistrer ». */
    fun onImagePicked(uri: String) {
        savedState[KEY_IMAGE_URI] = uri
        savedState[KEY_IMAGE_REMOVED] = false
    }

    /** Retire la photo (choix en attente ou photo enregistrée) ; effectif à « Enregistrer ». */
    fun onImageRemoved() {
        savedState[KEY_IMAGE_URI] = null
        savedState[KEY_IMAGE_REMOVED] = true
    }

    /** Crée une catégorie et la sélectionne. */
    fun onCreateCategory(name: String) {
        viewModelScope.launch {
            try {
                val category = products.createCategory(name)
                savedState[KEY_CATEGORY] = category.id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.tryEmit(ProductFormEvent.SaveFailed)
            }
        }
    }

    /** [input] porte les champs texte ; catégorie, unité et date sont complétées ici depuis l'état conservé. */
    fun onSave(input: ProductFormInput) {
        if (!saving.compareAndSet(expect = false, update = true)) return
        val complete = input.copy(
            id = productId,
            categoryId = categoryId.value,
            unit = ProductUnit.fromStored(unitName.value),
            expiryDate = expiry.value
        )
        val imageChange = when {
            pendingImageUri.value != null -> ProductImageChange.Replace(pendingImageUri.value!!)
            imageRemoved.value -> ProductImageChange.Remove
            else -> ProductImageChange.Keep
        }
        when (val parsed = ProductFormParser.parse(complete)) {
            is ProductFormParseResult.Invalid -> {
                errors.value = parsed.errors
                saving.value = false
            }
            is ProductFormParseResult.Valid -> {
                errors.value = emptySet()
                viewModelScope.launch {
                    try {
                        products.saveProduct(parsed.draft.copy(imageChange = imageChange))
                        _events.tryEmit(ProductFormEvent.Saved)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: ProductImageException) {
                        _events.tryEmit(ProductFormEvent.ImageFailed(e.reason))
                    } catch (e: ProductException.DuplicateBarcode) {
                        _events.tryEmit(ProductFormEvent.DuplicateBarcode(e.existingProductId))
                    } catch (e: ProductException.DuplicateSku) {
                        _events.tryEmit(ProductFormEvent.DuplicateSku(e.existingProductId))
                    } catch (e: ProductException.Invalid) {
                        errors.value = e.errors.toSet()
                    } catch (e: Exception) {
                        _events.tryEmit(ProductFormEvent.SaveFailed)
                    } finally {
                        saving.value = false
                    }
                }
            }
        }
    }

    private fun Product.toInput() = ProductFormInput(
        id = id,
        name = name,
        description = description.orEmpty(),
        sku = sku.orEmpty(),
        barcode = barcode.orEmpty(),
        categoryId = categoryId,
        unit = unit,
        purchasePrice = purchasePriceMinor?.let(Money::formatForInput).orEmpty(),
        salePrice = Money.formatForInput(salePriceMinor),
        initialStock = "",
        minStock = if (minStockMilli == 0L) "" else ProductQuantityFormat.formatForInput(minStockMilli),
        expiryDate = expiryDate
    )

    companion object {
        const val ARG_PRODUCT_ID = "productId"
        private const val KEY_CATEGORY = "form_category"
        private const val KEY_UNIT = "form_unit"
        private const val KEY_EXPIRY = "form_expiry"
        private const val KEY_PREFILLED = "form_prefilled"
        private const val KEY_IMAGE_URI = "form_image_uri"
        private const val KEY_IMAGE_REMOVED = "form_image_removed"
    }
}
