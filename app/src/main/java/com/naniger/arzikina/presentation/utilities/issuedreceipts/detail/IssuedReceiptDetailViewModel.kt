package com.naniger.arzikina.presentation.utilities.issuedreceipts.detail

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.issuedreceipt.DeleteIssuedReceiptUseCase
import com.naniger.arzikina.domain.issuedreceipt.IssuedReceiptException
import com.naniger.arzikina.domain.issuedreceipt.IssuedReceiptPdfFile
import com.naniger.arzikina.domain.issuedreceipt.PrepareIssuedReceiptPdfUseCase
import com.naniger.arzikina.domain.model.IssuedReceipt
import com.naniger.arzikina.domain.repository.IssuedReceiptRepository
import com.naniger.arzikina.domain.repository.IssuerProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface IssuedReceiptDetailState {
    data object Loading : IssuedReceiptDetailState
    data object NotFound : IssuedReceiptDetailState
    data class Loaded(val receipt: IssuedReceipt) : IssuedReceiptDetailState
}

sealed interface IssuedReceiptDetailEvent {
    data class Message(@StringRes val messageRes: Int) : IssuedReceiptDetailEvent
    data class PdfReady(val file: IssuedReceiptPdfFile, val share: Boolean) : IssuedReceiptDetailEvent

    /** Reçu supprimé : l'écran doit se fermer. */
    data object Deleted : IssuedReceiptDetailEvent
}

/**
 * Consultation d'un reçu enregistré (lecture seule) : aperçu identique au PDF, ouverture, partage,
 * modification (écran du formulaire) et suppression. Les données sont rechargées à chaque retour
 * sur l'écran ([load]) pour refléter une modification.
 */
@HiltViewModel
class IssuedReceiptDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val receipts: IssuedReceiptRepository,
    private val profiles: IssuerProfileRepository,
    private val preparePdf: PrepareIssuedReceiptPdfUseCase,
    private val deleteReceipt: DeleteIssuedReceiptUseCase
) : ViewModel() {

    val receiptId: Long = savedStateHandle.get<Long>("receiptId") ?: 0L

    private val _state = MutableStateFlow<IssuedReceiptDetailState>(IssuedReceiptDetailState.Loading)
    val state: StateFlow<IssuedReceiptDetailState> = _state.asStateFlow()

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    private val _events = MutableSharedFlow<IssuedReceiptDetailEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<IssuedReceiptDetailEvent> = _events.asSharedFlow()

    fun load() {
        viewModelScope.launch {
            val receipt = receipts.getReceipt(receiptId)
            _state.value = if (receipt == null) IssuedReceiptDetailState.NotFound else IssuedReceiptDetailState.Loaded(receipt)
        }
    }

    fun logoFile(fileName: String): File? = profiles.logoFile(fileName)

    fun onOpenPdf(language: String) = pdfAction(language, share = false)
    fun onSharePdf(language: String) = pdfAction(language, share = true)

    private fun pdfAction(language: String, share: Boolean) = runExclusive {
        try {
            _events.tryEmit(IssuedReceiptDetailEvent.PdfReady(preparePdf(receiptId, language), share))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IssuedReceiptException) {
            _events.tryEmit(IssuedReceiptDetailEvent.Message(R.string.issued_receipt_history_pdf_failed))
        }
    }

    /** À appeler APRÈS la confirmation de l'utilisateur. */
    fun onDeleteConfirmed() = runExclusive {
        try {
            deleteReceipt(receiptId)
            _events.tryEmit(IssuedReceiptDetailEvent.Deleted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _events.tryEmit(IssuedReceiptDetailEvent.Message(R.string.issued_receipt_history_delete_failed))
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
}
