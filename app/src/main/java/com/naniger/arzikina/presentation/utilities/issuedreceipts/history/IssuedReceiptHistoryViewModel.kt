package com.naniger.arzikina.presentation.utilities.issuedreceipts.history

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.issuedreceipt.DeleteIssuedReceiptUseCase
import com.naniger.arzikina.domain.issuedreceipt.IssuedReceiptException
import com.naniger.arzikina.domain.issuedreceipt.IssuedReceiptPdfFile
import com.naniger.arzikina.domain.issuedreceipt.PrepareIssuedReceiptPdfUseCase
import com.naniger.arzikina.domain.model.IssuedReceiptSummary
import com.naniger.arzikina.domain.repository.IssuedReceiptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

sealed interface IssuedReceiptHistoryUiState {
    data object Loading : IssuedReceiptHistoryUiState
    data object Error : IssuedReceiptHistoryUiState

    /** [filtersActive] distingue « aucun reçu du tout » de « aucun résultat pour ce filtre ». */
    data class Loaded(val items: List<IssuedReceiptSummary>, val filtersActive: Boolean) : IssuedReceiptHistoryUiState
}

sealed interface IssuedReceiptHistoryEvent {
    data class Message(@StringRes val messageRes: Int) : IssuedReceiptHistoryEvent

    /** PDF prêt : à ouvrir ([share] faux) ou à partager ([share] vrai). */
    data class PdfReady(val file: IssuedReceiptPdfFile, val share: Boolean) : IssuedReceiptHistoryEvent
}

/**
 * Historique des reçus émis : recherche (numéro, client, description) et période. Le filtrage est
 * fait en SQL (voir `IssuedReceiptDao.observeSummaries`) ; la saisie est temporisée pour ne pas
 * relancer une requête à chaque caractère.
 */
@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class IssuedReceiptHistoryViewModel @Inject constructor(
    private val receipts: IssuedReceiptRepository,
    private val preparePdf: PrepareIssuedReceiptPdfUseCase,
    private val deleteReceipt: DeleteIssuedReceiptUseCase
) : ViewModel() {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val _filters = MutableStateFlow(IssuedReceiptHistoryFilters())
    val filters: StateFlow<IssuedReceiptHistoryFilters> = _filters.asStateFlow()

    private val _working = MutableStateFlow(false)

    /** Vrai pendant une génération de PDF ou une suppression (les actions sont ignorées pendant ce temps). */
    val working: StateFlow<Boolean> = _working.asStateFlow()

    private val _events = MutableSharedFlow<IssuedReceiptHistoryEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<IssuedReceiptHistoryEvent> = _events.asSharedFlow()

    val uiState: StateFlow<IssuedReceiptHistoryUiState> = _filters
        .debounce { if (it.query.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .flatMapLatest { f ->
            receipts.observeReceipts(f.toDomain())
                .map<List<IssuedReceiptSummary>, IssuedReceiptHistoryUiState> { IssuedReceiptHistoryUiState.Loaded(it, f.isActive) }
                .catch { emit(IssuedReceiptHistoryUiState.Error) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IssuedReceiptHistoryUiState.Loading)

    fun onQueryChange(query: String) = _filters.update { it.copy(query = query) }
    fun onFromChange(localMillis: Long) = _filters.update { it.withFrom(localMillis, zone) }
    fun onToChange(localMillis: Long) = _filters.update { it.withTo(localMillis, zone) }
    fun onClearDates() = _filters.update { it.clearDates() }
    fun onResetFilters() = _filters.update { IssuedReceiptHistoryFilters() }

    fun onOpenPdf(receiptId: Long, language: String) = pdfAction(receiptId, language, share = false)
    fun onSharePdf(receiptId: Long, language: String) = pdfAction(receiptId, language, share = true)

    private fun pdfAction(receiptId: Long, language: String, share: Boolean) = runExclusive {
        try {
            _events.tryEmit(IssuedReceiptHistoryEvent.PdfReady(preparePdf(receiptId, language), share))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IssuedReceiptException) {
            _events.tryEmit(IssuedReceiptHistoryEvent.Message(R.string.issued_receipt_history_pdf_failed))
        }
    }

    /** À appeler APRÈS la confirmation de l'utilisateur. */
    fun onDeleteConfirmed(receiptId: Long) = runExclusive {
        try {
            deleteReceipt(receiptId)
            _events.tryEmit(IssuedReceiptHistoryEvent.Message(R.string.issued_receipt_history_deleted))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _events.tryEmit(IssuedReceiptHistoryEvent.Message(R.string.issued_receipt_history_delete_failed))
        }
    }

    private fun runExclusive(block: suspend () -> Unit) {
        if (!_working.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                block()
            } finally {
                _working.value = false
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
