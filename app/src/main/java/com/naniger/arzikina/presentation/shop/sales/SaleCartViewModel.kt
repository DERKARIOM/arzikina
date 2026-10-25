package com.naniger.arzikina.presentation.shop.sales

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.AccountType
import com.naniger.arzikina.domain.model.ReceiptDiscount
import com.naniger.arzikina.domain.repository.AccountRepository
import com.naniger.arzikina.domain.repository.ProductRepository
import com.naniger.arzikina.domain.repository.SaleRepository
import com.naniger.arzikina.domain.repository.ShopRepository
import com.naniger.arzikina.domain.shop.Product
import com.naniger.arzikina.domain.shop.ProductUnit
import com.naniger.arzikina.domain.shop.SaleCalculator
import com.naniger.arzikina.domain.shop.SaleException
import com.naniger.arzikina.domain.shop.SaleLineDraft
import com.naniger.arzikina.domain.shop.SaleLineRequest
import com.naniger.arzikina.domain.shop.SaleRequest
import com.naniger.arzikina.domain.shop.StockMovementType
import com.naniger.arzikina.domain.shop.StockRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Problème bloquant sur une ligne du panier. */
enum class CartLineProblem { UNAVAILABLE, INSUFFICIENT_STOCK, INVALID_QUANTITY }

data class CartLineUi(
    val productId: Long,
    val name: String,
    val unit: ProductUnit,
    val unitPriceMinor: Long,
    val quantityMilli: Long,
    val stockMilli: Long,
    val lineTotalMinor: Long?,
    val problem: CartLineProblem?
)

data class SaleCartUiState(
    val ready: Boolean = false,
    val currencyCode: String = "",
    val lines: List<CartLineUi> = emptyList(),
    val subtotalMinor: Long = 0L,
    val discountMinor: Long = 0L,
    val totalMinor: Long = 0L,
    val discountMode: DiscountMode = DiscountMode.NONE,
    val discountInvalid: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
    val canConfirm: Boolean = false,
    val submitting: Boolean = false
)

/** Raison d'un refus ou d'un échec, traduite en texte par le Fragment (le ViewModel ne connaît pas `Context`). */
enum class SaleCartError { UNAVAILABLE, OUT_OF_STOCK, STOCK_LIMIT, INSUFFICIENT_STOCK, QUANTITY, AMOUNT, ZERO_TOTAL, ACCOUNT, CURRENCY, SHOP, GENERIC }

sealed interface SaleCartEvent {
    data class Confirmed(val saleId: Long) : SaleCartEvent
    data class Failed(val error: SaleCartError) : SaleCartEvent
}

/**
 * Panier de vente. Ne contient que le choix de l'utilisateur (produits + quantités, remise, client,
 * compte) ; prix, stock et totaux sont toujours recalculés depuis les données courantes, et la
 * validation relit tout dans sa transaction. L'état saisi survit à la mort du processus
 * ([SavedStateHandle]).
 *
 * [draftKey] est généré une seule fois à l'ouverture du panier : valider deux fois le même panier
 * (double clic, nouvelle tentative après une coupure) ne crée jamais deux ventes.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SaleCartViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val products: ProductRepository,
    private val shops: ShopRepository,
    accountsRepository: AccountRepository,
    private val sales: SaleRepository
) : ViewModel() {

    private val draftKey: String = savedState.get<String>(KEY_DRAFT) ?: UUID.randomUUID().toString().also {
        savedState[KEY_DRAFT] = it
    }

    /** Produit -> quantité en milli-unités, dans l'ordre d'ajout. */
    private val cart = MutableStateFlow(restoreCart())
    private val discountMode = MutableStateFlow(DiscountMode.entries.getOrElse(savedState.get<Int>(KEY_MODE) ?: 0) { DiscountMode.NONE })
    private val discountInput = MutableStateFlow(savedState.get<String>(KEY_DISCOUNT).orEmpty())
    private val chosenAccountId = MutableStateFlow(savedState.get<Long>(KEY_ACCOUNT))
    private val submitting = MutableStateFlow(false)

    /** Valeurs initiales des champs texte (le Fragment les écrit une seule fois, sans les réinjecter ensuite). */
    val initialDiscountInput: String get() = savedState.get<String>(KEY_DISCOUNT).orEmpty()
    val initialCustomerName: String get() = savedState.get<String>(KEY_CUSTOMER).orEmpty()
    val initialCustomerPhone: String get() = savedState.get<String>(KEY_PHONE).orEmpty()

    private val _events = MutableSharedFlow<SaleCartEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SaleCartEvent> = _events.asSharedFlow()

    private data class Draft(
        val cart: Map<Long, Long>,
        val mode: DiscountMode,
        val discountInput: String,
        val accountId: Long?,
        val submitting: Boolean
    )

    private val draft = combine(cart, discountMode, discountInput, chosenAccountId, submitting) { c, m, d, a, s ->
        Draft(c, m, d, a, s)
    }

    val uiState: StateFlow<SaleCartUiState> = combine(draft, shops.observeShop(), accountsRepository.observeAccounts()) { d, shop, accounts ->
        Triple(d, shop, accounts)
    }.flatMapLatest { (d, shop, accounts) ->
        if (shop == null) {
            flowOf(SaleCartUiState())
        } else {
            productsFlow(d.cart.keys).map { loaded ->
                build(d, shop.currencyCode, shop.accountId, accounts, loaded)
            }
        }
    }.catch { emit(SaleCartUiState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SaleCartUiState())

    // --- Actions ---------------------------------------------------------------------------------

    fun addProduct(productId: Long) {
        viewModelScope.launch {
            val product = products.getProduct(productId)
            when {
                product == null || product.isArchived -> fail(SaleCartError.UNAVAILABLE)
                product.stockMilli <= 0L -> fail(SaleCartError.OUT_OF_STOCK)
                else -> {
                    val next = (cart.value[productId] ?: 0L) + StockRules.QUANTITY_UNIT_MILLI
                    if (next > product.stockMilli) fail(SaleCartError.STOCK_LIMIT) else setQuantity(productId, next)
                }
            }
        }
    }

    /** `+` : une unité de plus, sans dépasser le stock. */
    fun increment(productId: Long) = addProduct(productId)

    /** `-` : une unité de moins ; la ligne disparaît sous une unité (les fractions se saisissent directement). */
    fun decrement(productId: Long) {
        val current = cart.value[productId] ?: return
        val next = current - StockRules.QUANTITY_UNIT_MILLI
        if (next <= 0L) remove(productId) else setQuantity(productId, next)
    }

    /** Quantité exacte saisie par l'utilisateur ; `<= 0` retire la ligne. Les dépassements de stock restent affichés en erreur. */
    fun setQuantity(productId: Long, quantityMilli: Long) {
        if (quantityMilli <= 0L) return remove(productId)
        updateCart { it + (productId to quantityMilli) }
    }

    fun remove(productId: Long) = updateCart { it - productId }

    fun onDiscountModeChange(mode: DiscountMode) {
        discountMode.value = mode
        savedState[KEY_MODE] = mode.ordinal
    }

    fun onDiscountInputChange(input: String) {
        discountInput.value = input
        savedState[KEY_DISCOUNT] = input
    }

    fun onCustomerNameChange(name: String) {
        savedState[KEY_CUSTOMER] = name
    }

    fun onCustomerPhoneChange(phone: String) {
        savedState[KEY_PHONE] = phone
    }

    fun onAccountChosen(accountId: Long) {
        chosenAccountId.value = accountId
        savedState[KEY_ACCOUNT] = accountId
    }

    fun confirm() {
        val state = uiState.value
        val account = state.selectedAccount
        // Garde anti double clic : un seul envoi à la fois, et seulement si le panier est valide.
        if (!state.canConfirm || account == null || !submitting.compareAndSet(expect = false, update = true)) return
        val discount = (SaleFormParser.parseDiscount(discountMode.value, discountInput.value) as? DiscountParse.Valid)
            ?.discount ?: ReceiptDiscount.None
        val request = SaleRequest(
            draftKey = draftKey,
            lines = cart.value.map { SaleLineRequest(it.key, it.value) },
            accountId = account.id,
            discount = discount,
            customerName = savedState.get<String>(KEY_CUSTOMER),
            customerPhone = savedState.get<String>(KEY_PHONE)
        )
        viewModelScope.launch {
            try {
                val sale = sales.confirmSale(request)
                // Volontairement PAS de remise à zéro de `submitting` : le panier est quitté, le bouton
                // doit rester inactif jusqu'à la navigation (aucun second envoi possible).
                _events.tryEmit(SaleCartEvent.Confirmed(sale.id))
            } catch (e: SaleException) {
                submitting.value = false
                fail(e.toCartError())
            } catch (e: Exception) {
                // Échec technique : le même panier (même clé) pourra être renvoyé sans risque de doublon.
                submitting.value = false
                fail(SaleCartError.GENERIC)
            }
        }
    }

    // --- Internes --------------------------------------------------------------------------------

    private fun fail(error: SaleCartError) {
        _events.tryEmit(SaleCartEvent.Failed(error))
    }

    private fun updateCart(transform: (Map<Long, Long>) -> Map<Long, Long>) {
        cart.update(transform)
        savedState[KEY_CART] = cart.value.flatMap { listOf(it.key, it.value) }.toLongArray()
    }

    private fun restoreCart(): Map<Long, Long> {
        val flat = savedState.get<LongArray>(KEY_CART) ?: return emptyMap()
        val restored = LinkedHashMap<Long, Long>()
        var i = 0
        while (i + 1 < flat.size) {
            restored[flat[i]] = flat[i + 1]
            i += 2
        }
        return restored
    }

    private fun productsFlow(ids: Collection<Long>) =
        if (ids.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(ids.map { id -> products.observeProduct(id).map { id to it } }) { pairs -> pairs.toMap() }
        }

    private fun build(
        draft: Draft,
        currencyCode: String,
        shopAccountId: Long?,
        allAccounts: List<Account>,
        loaded: Map<Long, Product?>
    ): SaleCartUiState {
        val lines = draft.cart.map { (productId, quantity) -> lineOf(productId, quantity, loaded[productId]) }

        val priced = lines.filter { it.problem != CartLineProblem.UNAVAILABLE && it.lineTotalMinor != null }
        val drafts = priced.map { SaleLineDraft(it.productId, it.unitPriceMinor, it.quantityMilli) }
        val parsed = SaleFormParser.parseDiscount(draft.mode, draft.discountInput)
        val discount = (parsed as? DiscountParse.Valid)?.discount ?: ReceiptDiscount.None
        val totals = SaleCalculator.totalsOrNull(drafts, discount)

        val accounts = allAccounts.filter {
            it.currencyCode == currencyCode && it.type != AccountType.SAVINGS_GOAL && it.type != AccountType.CREDIT_CARD
        }
        val selected = accounts.firstOrNull { it.id == draft.accountId }
            ?: accounts.firstOrNull { it.id == shopAccountId }
            ?: accounts.firstOrNull { it.type == AccountType.BOUTIQUE }
            ?: accounts.firstOrNull()

        val canConfirm = lines.isNotEmpty() && lines.all { it.problem == null } && parsed is DiscountParse.Valid &&
            totals != null && totals.totalMinor > 0L && selected != null && !draft.submitting
        return SaleCartUiState(
            ready = true,
            currencyCode = currencyCode,
            lines = lines,
            subtotalMinor = totals?.subtotalMinor ?: 0L,
            discountMinor = totals?.discountMinor ?: 0L,
            totalMinor = totals?.totalMinor ?: 0L,
            discountMode = draft.mode,
            discountInvalid = parsed is DiscountParse.Invalid,
            accounts = accounts,
            selectedAccount = selected,
            canConfirm = canConfirm,
            submitting = draft.submitting
        )
    }

    private fun lineOf(productId: Long, quantityMilli: Long, product: Product?): CartLineUi {
        if (product == null || product.isArchived) {
            return CartLineUi(productId, product?.name.orEmpty(), ProductUnit.OTHER, 0L, quantityMilli, 0L, null, CartLineProblem.UNAVAILABLE)
        }
        val problem = when {
            StockRules.validate(StockMovementType.SALE, -quantityMilli, null, product.unit) != null -> CartLineProblem.INVALID_QUANTITY
            !StockRules.canSell(product.stockMilli, quantityMilli) -> CartLineProblem.INSUFFICIENT_STOCK
            else -> null
        }
        return CartLineUi(
            productId = productId,
            name = product.name,
            unit = product.unit,
            unitPriceMinor = product.salePriceMinor,
            quantityMilli = quantityMilli,
            stockMilli = product.stockMilli,
            lineTotalMinor = SaleCalculator.lineTotalOrNull(SaleLineDraft(productId, product.salePriceMinor, quantityMilli)),
            problem = problem
        )
    }

    private fun SaleException.toCartError(): SaleCartError = when (this) {
        is SaleException.ProductUnavailable -> SaleCartError.UNAVAILABLE
        is SaleException.InsufficientStock -> SaleCartError.INSUFFICIENT_STOCK
        is SaleException.InvalidQuantity, is SaleException.EmptyCart -> SaleCartError.QUANTITY
        is SaleException.InvalidAmount -> SaleCartError.AMOUNT
        is SaleException.ZeroTotal -> SaleCartError.ZERO_TOTAL
        is SaleException.AccountUnavailable -> SaleCartError.ACCOUNT
        is SaleException.CurrencyMismatch -> SaleCartError.CURRENCY
        is SaleException.ShopNotReady -> SaleCartError.SHOP
        is SaleException.NotFound, is SaleException.NoSession,
        // Erreurs propres à l'émission du reçu / à l'annulation : jamais levées par la validation d'un panier.
        is SaleException.NotConfirmed, is SaleException.IssuerProfileIncomplete -> SaleCartError.GENERIC
    }

    private companion object {
        const val KEY_DRAFT = "draftKey"
        const val KEY_CART = "cart"
        const val KEY_MODE = "discountMode"
        const val KEY_DISCOUNT = "discountInput"
        const val KEY_CUSTOMER = "customerName"
        const val KEY_PHONE = "customerPhone"
        const val KEY_ACCOUNT = "accountId"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
