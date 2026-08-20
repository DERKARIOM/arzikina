package com.arzikina.ne.presentation.utilities.receipts

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.data.receipts.ReceiptFileStorage
import com.arzikina.ne.data.receipts.ReceiptIntentLauncher
import com.arzikina.ne.data.receipts.ReceiptPdfRenderer
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.Receipt
import com.arzikina.ne.domain.repository.ReceiptRepository
import com.arzikina.ne.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Événements ponctuels de "Détail du reçu" — même schéma que `presentation.settings.BackupEvent`
 * (voir aussi `ReceiptImportEvent`) : jamais porté par [ReceiptDetailViewModel.uiState]. */
sealed interface ReceiptDetailEvent {
    data object ShareFailed : ReceiptDetailEvent
    data object OpenWithFailed : ReceiptDetailEvent
}

/**
 * ViewModel de "Détail du reçu" (cahier des charges "Gestion des reçus", section 6) : métadonnées,
 * aperçu de la première page du PDF, renommer/supprimer/partager/ouvrir avec une autre application.
 *
 * [ReceiptFileStorage]/[ReceiptPdfRenderer]/[ReceiptIntentLauncher] injectés directement (pas de
 * détour par [ReceiptRepository]) : même principe documenté sur `ExternalAppLauncher` — ce sont des
 * utilitaires techniques hors du domaine (Uri/Bitmap/Intent irréductibles), pas des capacités
 * métier substituables. [ReceiptRepository] reste seul responsable des métadonnées (Room).
 */
@HiltViewModel
class ReceiptDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val receiptRepository: ReceiptRepository,
    private val receiptFileStorage: ReceiptFileStorage,
    private val receiptPdfRenderer: ReceiptPdfRenderer,
    private val receiptIntentLauncher: ReceiptIntentLauncher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    val receiptId: Long = savedStateHandle.get<Long>(RECEIPT_ID_ARG) ?: 0L

    private val _events = MutableSharedFlow<ReceiptDetailEvent>()
    val events: SharedFlow<ReceiptDetailEvent> = _events.asSharedFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)

    /** `null` tant que le rendu n'est pas terminé (ou a échoué, voir [ReceiptPdfRenderer.renderFirstPage] —
     * jamais distingué explicitement d'un "toujours en cours" : voir `ReceiptDetailFragment`, qui
     * affiche simplement un texte de repli tant que ce flux reste `null`, cohérent dans les deux
     * cas). */
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    /**
     * Réactif (voir [ReceiptRepository.observeReceipts]) plutôt qu'un simple chargement ponctuel via
     * [ReceiptRepository.getReceipt] : reflète immédiatement un renommage (voir [renameReceipt]) sans
     * rechargement explicite — même principe que `LoanDetailViewModel.uiState`.
     */
    val uiState: StateFlow<AppResult<Receipt>> = receiptRepository.observeReceipts()
        .map<List<Receipt>, AppResult<Receipt>> { receipts ->
            receipts.find { it.id == receiptId }?.let { AppResult.Success(it) }
                ?: AppResult.Error("Reçu introuvable")
        }
        .catch { throwable -> emit(AppResult.Error(throwable.message ?: "Erreur inconnue", throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )

    init {
        loadPreview()
    }

    /**
     * Rendu UNIQUE au chargement de l'écran (pas re-déclenché à chaque émission de [uiState], ex.
     * après un renommage — voir sa doc) : le fichier PDF lui-même ne change jamais pour un reçu
     * donné (seul [Receipt.fileName] est modifiable, voir [ReceiptRepository.saveReceipt]), un
     * nouveau rendu serait un travail strictement redondant.
     */
    private fun loadPreview() {
        viewModelScope.launch {
            val receipt = receiptRepository.getReceipt(receiptId) ?: return@launch
            val bitmap = withContext(ioDispatcher) {
                val file = receiptFileStorage.resolveFile(receipt.localPath)
                receiptPdfRenderer.renderFirstPage(file, PREVIEW_WIDTH_PX)
            }
            _previewBitmap.value = bitmap
        }
    }

    /**
     * Voir cahier des charges section 10 : ne modifie que [Receipt.fileName], jamais
     * [Receipt.localPath] ni le fichier physique (voir [ReceiptRepository.saveReceipt]).
     *
     * [newFileName] vide/blanc : no-op silencieux, filet de sécurité SEULEMENT — la validation
     * visible (erreur affichée sur le champ) revient à `ReceiptDetailFragment` (voir sa boîte de
     * dialogue "Renommer"), même principe que `PersonPickerDialog.showAddPersonDialog` : ce
     * ViewModel ne doit jamais avoir à faire fermer/rouvrir un dialogue dont il ignore jusqu'à
     * l'existence.
     */
    fun renameReceipt(newFileName: String) {
        val trimmed = newFileName.trim()
        if (trimmed.isEmpty()) return
        val current = (uiState.value as? AppResult.Success)?.data ?: return
        if (trimmed == current.fileName) return
        viewModelScope.launch { receiptRepository.saveReceipt(current.copy(fileName = trimmed)) }
    }

    /** Supprime la ligne Room ET le fichier physique (voir [ReceiptRepository.deleteReceipt]) —
     * `ReceiptDetailFragment` navigue en arrière immédiatement après cet appel, sans attendre de
     * confirmation asynchrone (même convention que `LoanDetailFragment.confirmDelete`). */
    fun deleteReceipt() {
        viewModelScope.launch { receiptRepository.deleteReceipt(receiptId) }
    }

    fun shareReceipt() {
        val receipt = (uiState.value as? AppResult.Success)?.data ?: return
        val success = receiptIntentLauncher.share(receipt.localPath, receipt.mimeType)
        if (!success) {
            viewModelScope.launch { _events.emit(ReceiptDetailEvent.ShareFailed) }
        }
    }

    fun openWithAnotherApp() {
        val receipt = (uiState.value as? AppResult.Success)?.data ?: return
        val success = receiptIntentLauncher.openWithAnotherApp(receipt.localPath, receipt.mimeType)
        if (!success) {
            viewModelScope.launch { _events.emit(ReceiptDetailEvent.OpenWithFailed) }
        }
    }

    private companion object {
        const val RECEIPT_ID_ARG = "receiptId"

        /** Largeur cible du rendu de l'aperçu, en pixels — voir `fragment_receipt_detail.xml`
         * (`previewImage`) : suffisant pour un aperçu net sur un écran de téléphone courant sans
         * générer un bitmap inutilement volumineux (voir cahier des charges, "optimiser... la
         * consommation mémoire"). */
        const val PREVIEW_WIDTH_PX = 720
    }
}
