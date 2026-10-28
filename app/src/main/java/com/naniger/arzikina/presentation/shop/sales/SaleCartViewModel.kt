package com.naniger.arzikina.presentation.shop.sales

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.ReceiptDiscount
import com.naniger.arzikina.domain.repository.AccountRepository
import com.naniger.arzikina.domain.repository.ProductRepository
import com.naniger.arzikina.domain.repository.SaleRepository
import com.naniger.arzikina.domain.repository.ShopRepository
import com.naniger.arzikina.domain.shop.CartAddResult
import com.naniger.arzikina.domain.shop.CreateShopAccountError
import com.naniger.arzikina.domain.shop.CreateShopAccountResult
import com.naniger.arzikina.domain.shop.CreateShopAccountUseCase
import com.naniger.arzikina.domain.shop.Product
import com.naniger.arzikina.domain.shop.ProductUnit
import com.naniger.arzikina.domain.shop.SaleCalculator
import com.naniger.arzikina.domain.shop.SaleCartStore
import com.naniger.arzikina.domain.shop.SaleException
import com.naniger.arzikina.domain.shop.SaleLineDraft
import com.naniger.arzikina.domain.shop.SaleLineRequest
import com.naniger.arzikina.domain.shop.SaleRequest
import com.naniger.arzikina.domain.shop.ShopAccountPolicy
import com.naniger.arzikina.domain.shop.StockMovementType
import com.naniger.arzikina.domain.shop.StockRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    /** Aucun compte Boutique éligible : l'écran propose d'en créer un (voir [SaleCartViewModel.createShopAccount]). */
    val needsShopAccount: Boolean = false,
    val canConfirm: Boolean = false,
    val submitting: Boolean = false
)

/** Raison d'un refus ou d'un échec, traduite en texte par le Fragment (le ViewModel ne connaît pas `Context`). */
enum class SaleCartError { UNAVAILABLE, OUT_OF_STOCK, STOCK_LIMIT, INSUFFICIENT_STOCK, QUANTITY, AMOUNT, ZERO_TOTAL, ACCOUNT, ACCOUNT_TYPE, CURRENCY, SHOP, GENERIC }

sealed interface SaleCartEvent {
    data class Confirmed(val saleId: Long) : SaleCartEvent
    data class Failed(val error: SaleCartError) : SaleCartEvent

    /** Compte Boutique créé depuis l'écran de vente (déjà sélectionné pour l'encaissement). */
    data class ShopAccountCreated(val name: String) : SaleCartEvent

    /** Création refusée : le nom est à corriger ([CreateShopAccountError]). */
    data class ShopAccountRejected(val error: CreateShopAccountError) : SaleCartEvent
}

/**
 * Écran de vente. Les produits et quantités vivent dans le [SaleCartStore] partagé (alimenté aussi par la
 * liste des produits) ; la remise, le client et le compte saisis ici survivent à la mort du processus
 * ([SavedStateHandle]). Prix, stock et totaux sont toujours recalculés depuis les données courantes, et la
 * validation relit tout dans sa transaction.
 *
 * Seul un compte de type Boutique encaisse une vente ([ShopAccountPolicy]) : sans compte éligible, l'écran propose
 * d'en créer un sur place ([createShopAccount]) et la validation reste impossible.
 *
 * La clé de brouillon ([SaleCartStore.draftKey]) est stable pour un même panier : valider deux fois le même
 * panier (double clic, nouvelle tentative après une coupure) ne crée jamais deux ventes.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SaleCartViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val products: ProductRepository,
    private val shops: ShopRepository,
    accountsRepository: AccountRepository,
    private val sales: SaleRepository,
    private val store: SaleCartStore,
    private val createShopAccountUseCase: CreateShopAccountUseCase
) : ViewModel() {

    /** Produit -> quantité en milli-unités, dans l'ordre d'ajout (source de vérité : [SaleCartStore]). */
    private val cart: StateFlow<Map<Long, Long>> = store.lines
    private val discountMode = MutableStateFlow(DiscountMode.entries.getOrElse(savedState.get<Int>(KEY_MODE) ?: 0) { DiscountMode.NONE })
    private val discountInput = MutableStateFlow(savedState.get<String>(KEY_DISCOUNT).orEmpty())
    private val chosenAccountId = MutableStateFlow(savedState.get<Long>(KEY_ACCOUNT))
    private val submitting = MutableStateFlow(false)
    private val creatingAccount = MutableStateFlow(false)

    /** Vrai pendant la création d'un compte Boutique (le bouton de la fenêtre est alors inactif). */
    val isCreatingAccount: StateFlow<Boolean> = creatingAccount.asStateFlow()

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
            val result = if (product == null) {
                CartAddResult.UNAVAILABLE
            } else {
                store.tryAdd(productId, product.stockMilli, product.isArchived)
            }
            when (result) {
                CartAddResult.ADDED -> Unit
                CartAddResult.UNAVAILABLE -> fail(SaleCartError.UNAVAILABLE)
                CartAddResult.OUT_OF_STOCK -> fail(SaleCartError.OUT_OF_STOCK)
                CartAddResult.STOCK_LIMIT -> fail(SaleCartError.STOCK_LIMIT)
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
        store.setQuantity(productId, quantityMilli)
    }

    fun remove(productId: Long) = store.remove(productId)

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

    /**
     * Crée le compte Boutique qui recevra les ventes, puis le sélectionne. Un seul envoi à la fois (double clic) ;
     * un nom refusé est signalé par [SaleCartEvent.ShopAccountRejected] et le panier n'est pas touché.
     */
    fun createShopAccount(name: String) {
        if (!creatingAccount.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                when (val result = createShopAccountUseCase(name)) {
                    is CreateShopAccountResult.Created -> {
                        onAccountChosen(result.accountId)
                        _events.tryEmit(SaleCartEvent.ShopAccountCreated(result.name))
                    }
                    is CreateShopAccountResult.Rejected -> _events.tryEmit(SaleCartEvent.ShopAccountRejected(result.error))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.tryEmit(SaleCartEvent.ShopAccountRejected(CreateShopAccountError.FAILED))
            } finally {
                creatingAccount.value = false
            }
        }
    }

    fun confirm() {
        val state = uiState.value
        val account = state.selectedAccount
        // Garde anti double clic : un seul envoi à la fois, et seulement si le panier est valide.
        if (!state.canConfirm || account == null || !submitting.compareAndSet(expect = false, update = true)) return
        val discount = (SaleFormParser.parseDiscount(discountMode.value, discountInput.value) as? DiscountParse.Valid)
            ?.discount ?: ReceiptDiscount.None
        val request = SaleRequest(
            draftKey = store.draftKey,
            lines = cart.value.map { SaleLineRequest(it.key, it.value) },
            accountId = account.id,
            discount = discount,
            customerName = savedState.get<String>(KEY_CUSTOMER),
            customerPhone = savedState.get<String>(KEY_PHONE)
        )
        viewModelScope.launch {
            try {
                val sale = sales.confirmSale(request)
                // Vente enregistrée : le panier partagé repart de zéro (nouvelle clé d'idempotence).
                store.clear()
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

        // Seuls les comptes Boutique de la devise de la boutique encaissent (ShopAccountPolicy, règle unique).
        val accounts = ShopAccountPolicy.eligibleAccounts(allAccounts, currencyCode)
        val selected = ShopAccountPolicy.pickDefault(accounts, draft.accountId, shopAccountId)

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
            needsShopAccount = accounts.isEmpty(),
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
        is SaleException.NotShopAccount -> SaleCartError.ACCOUNT_TYPE
        is SaleException.CurrencyMismatch -> SaleCartError.CURRENCY
        is SaleException.ShopNotReady -> SaleCartError.SHOP
        is SaleException.NotFound, is SaleException.NoSession,
        // Erreurs propres à l'émission du reçu / à l'annulation : jamais levées par la validation d'un panier.
        is SaleException.NotConfirmed, is SaleException.IssuerProfileIncomplete -> SaleCartError.GENERIC
    }

    private companion object {
        const val KEY_MODE = "discountMode"
        const val KEY_DISCOUNT = "discountInput"
        const val KEY_CUSTOMER = "customerName"
        const val KEY_PHONE = "customerPhone"
        const val KEY_ACCOUNT = "accountId"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
