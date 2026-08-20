package com.arzikina.ne.presentation.utilities.receipts

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.data.receipts.ReceiptFileStorage
import com.arzikina.ne.data.receipts.ReceiptPdfRenderer
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.repository.ReceiptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * État du visualiseur — [pageBitmap] `null` ET [isLoading] `false` en même temps signifie "rendu
 * définitivement indisponible" pour la page courante (voir `ReceiptPdfViewerFragment.render`), même
 * convention que "Détail du reçu" (aucune distinction visuelle entre "en cours" et "jamais tenté"
 * n'est nécessaire ici : un seul texte de repli couvre les deux, voir sa doc).
 */
data class ReceiptPdfViewerUiState(
    val receiptName: String = "",
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val pageBitmap: Bitmap? = null,
    val isLoading: Boolean = true
)

/**
 * ViewModel du visualiseur PDF plein écran (cahier des charges "Gestion des reçus", section 7) —
 * réutilise [ReceiptPdfRenderer.open] (voir sa doc : PRÉCISÉMENT conçu pour être partagé entre
 * l'aperçu de "Détail du reçu", page unique, et cet écran, toutes les pages).
 *
 * Garde une [ReceiptPdfRenderer.Session] ouverte pour toute la durée de vie de l'écran (contrairement
 * à `ReceiptDetailViewModel.loadPreview`, qui ouvre/ferme immédiatement pour une seule page) — c'est
 * ce qui permet de rendre chaque page à la demande, au fil de la navigation, sans rouvrir le fichier
 * à chaque fois. Fermée explicitement dans [onCleared], jamais laissée ouverte au-delà de la durée
 * de vie de ce ViewModel (voir cahier des charges, "optimiser... la consommation mémoire").
 */
@HiltViewModel
class ReceiptPdfViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val receiptRepository: ReceiptRepository,
    private val receiptFileStorage: ReceiptFileStorage,
    private val receiptPdfRenderer: ReceiptPdfRenderer,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val receiptId: Long = savedStateHandle.get<Long>(RECEIPT_ID_ARG) ?: 0L

    private var session: ReceiptPdfRenderer.Session? = null

    private val _uiState = MutableStateFlow(ReceiptPdfViewerUiState())
    val uiState: StateFlow<ReceiptPdfViewerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val receipt = receiptRepository.getReceipt(receiptId)
            if (receipt == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            _uiState.update { it.copy(receiptName = receipt.fileName) }

            val opened = withContext(ioDispatcher) {
                receiptPdfRenderer.open(receiptFileStorage.resolveFile(receipt.localPath))
            }
            session = opened
            if (opened == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            _uiState.update { it.copy(pageCount = opened.pageCount) }
            renderCurrentPage()
        }
    }

    fun goToNextPage() {
        val state = _uiState.value
        if (state.pageIndex >= state.pageCount - 1) return
        _uiState.update { it.copy(pageIndex = it.pageIndex + 1) }
        renderCurrentPage()
    }

    fun goToPreviousPage() {
        val state = _uiState.value
        if (state.pageIndex <= 0) return
        _uiState.update { it.copy(pageIndex = it.pageIndex - 1) }
        renderCurrentPage()
    }

    private fun renderCurrentPage() {
        val currentSession = session ?: return
        val index = _uiState.value.pageIndex
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, pageBitmap = null) }
            val bitmap = withContext(ioDispatcher) { currentSession.renderPage(index, PAGE_WIDTH_PX) }
            // Revérifié : la page affichée a pu changer pendant le rendu (appui rapide et répété sur
            // Suivant) — ne jamais écraser l'état d'une page plus récente avec le résultat tardif
            // d'un rendu obsolète.
            if (_uiState.value.pageIndex == index) {
                _uiState.update { it.copy(pageBitmap = bitmap, isLoading = false) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        session?.close()
        session = null
    }

    private companion object {
        const val RECEIPT_ID_ARG = "receiptId"

        /** Plus large que `ReceiptDetailViewModel.PREVIEW_WIDTH_PX` : cet écran occupe tout l'écran,
         * un rendu plus net y est justifié (voir cahier des charges, "excellente lisibilité"). */
        const val PAGE_WIDTH_PX = 1080
    }
}
