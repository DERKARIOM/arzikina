package com.naniger.arzikina.presentation.shop.sales

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.repository.AccountRepository
import com.naniger.arzikina.domain.repository.SaleRepository
import com.naniger.arzikina.domain.shop.IssueSaleReceiptUseCase
import com.naniger.arzikina.domain.shop.Sale
import com.naniger.arzikina.domain.shop.SaleException
import com.naniger.arzikina.domain.shop.SaleStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SaleDetailUiState {
    data object Loading : SaleDetailUiState
    data object NotFound : SaleDetailUiState
    data class Loaded(
        val sale: Sale,
        val account: Account?,
        /** Une opération (reçu, annulation) est en cours : les boutons sont désactivés. */
        val busy: Boolean,
        /** Le profil émetteur est incomplet : un reçu ne peut pas être généré avant de le compléter. */
        val profileIncomplete: Boolean
    ) : SaleDetailUiState {
        val canIssueReceipt: Boolean get() = sale.status == SaleStatus.CONFIRMED && sale.issuedReceiptId == null
        val canCancel: Boolean get() = sale.status == SaleStatus.CONFIRMED
    }
}

sealed interface SaleDetailEvent {
    data class Message(@StringRes val resId: Int) : SaleDetailEvent
    data class OpenReceipt(val receiptId: Long) : SaleDetailEvent
}

/**
 * Détail d'une vente : consultation, émission du reçu (réutilise le module « Reçus émis ») et annulation.
 * N'utilise aucun Context : les textes dépendant de la langue sont fournis par le fragment.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ShopSaleDetailViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val sales: SaleRepository,
    private val accounts: AccountRepository,
    private val issueReceipt: IssueSaleReceiptUseCase
) : ViewModel() {

    private val saleId: Long = savedState.get<Long>(ARG_SALE_ID) ?: 0L
    private val busy = MutableStateFlow(false)
    private val profileIncomplete = MutableStateFlow(false)

    private val _events = MutableSharedFlow<SaleDetailEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SaleDetailEvent> = _events.asSharedFlow()

    val uiState: StateFlow<SaleDetailUiState> = sales.observeSale(saleId)
        .flatMapLatest { sale ->
            if (sale == null) {
                flowOf(SaleDetailUiState.NotFound)
            } else {
                val account = flow { emit(sale.accountId?.let { accounts.getAccount(it) }) }
                combine(account, busy, profileIncomplete) { acc, isBusy, incomplete ->
                    SaleDetailUiState.Loaded(sale, acc, isBusy, incomplete) as SaleDetailUiState
                }
            }
        }
        .catch { emit(SaleDetailUiState.NotFound) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SaleDetailUiState.Loading)

    /**
     * Appelé à l'ouverture de l'écran. Juste après une vente (argument `autoReceipt`), le reçu est émis
     * automatiquement, une seule fois même après rotation d'écran ; un profil incomplet n'est alors qu'un indice.
     */
    fun onScreenShown(defaultCustomerName: String, descriptionPrefix: String) {
        val requested = savedState.get<Boolean>(ARG_AUTO_RECEIPT) ?: false
        if (!requested || savedState.get<Boolean>(KEY_AUTO_DONE) == true) return
        savedState[KEY_AUTO_DONE] = true
        issue(defaultCustomerName, descriptionPrefix, openAfter = false, silent = true)
    }

    /** Action explicite : génère puis ouvre le reçu, ou ouvre celui qui existe déjà. */
    fun onReceiptClicked(defaultCustomerName: String, descriptionPrefix: String) {
        issue(defaultCustomerName, descriptionPrefix, openAfter = true, silent = false)
    }

    fun cancelSale(reason: String?) {
        if (!busy.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            val message = try {
                sales.cancelSale(saleId, reason)
                R.string.shop_sale_cancel_done
            } catch (e: CancellationException) {
                throw e
            } catch (e: SaleException) {
                R.string.shop_sale_cancel_failed
            } catch (e: Exception) {
                R.string.shop_sale_cancel_failed
            } finally {
                busy.value = false
            }
            _events.tryEmit(SaleDetailEvent.Message(message))
        }
    }

    private fun issue(name: String, prefix: String, openAfter: Boolean, silent: Boolean) {
        if (!busy.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                val receiptId = issueReceipt(saleId, name, prefix)
                profileIncomplete.value = false
                if (openAfter) _events.tryEmit(SaleDetailEvent.OpenReceipt(receiptId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: SaleException.IssuerProfileIncomplete) {
                profileIncomplete.value = true
                if (!silent) _events.tryEmit(SaleDetailEvent.Message(R.string.shop_receipt_profile_incomplete))
            } catch (e: Exception) {
                if (!silent) _events.tryEmit(SaleDetailEvent.Message(R.string.shop_receipt_failed))
            } finally {
                busy.value = false
            }
        }
    }

    companion object {
        const val ARG_SALE_ID = "saleId"
        const val ARG_AUTO_RECEIPT = "autoReceipt"
        private const val KEY_AUTO_DONE = "autoReceiptDone"
    }
}
