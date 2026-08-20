package com.arzikina.ne.presentation.utilities.receipts

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.data.receipts.ReceiptFileStorage
import com.arzikina.ne.data.receipts.ReceiptIntentLauncher
import com.arzikina.ne.data.receipts.ReceiptPdfRenderer
import com.arzikina.ne.data.receipts.ReceiptTextExtractor
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.Receipt
import com.arzikina.ne.domain.repository.ReceiptRepository
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.ReceiptAmountParser
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
import kotlinx.coroutines.flow.combine
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
 * État du bouton "Détecter le montant" (`detectAmountButton`, voir `fragment_receipt_detail.xml`)
 * — dérivé de trois sources à la fois ([ReceiptDetailViewModel.uiState],
 * [ReceiptDetailViewModel.suggestedAmountMinor], [ReceiptDetailViewModel.isDetectingAmount]) : centralisé
 * ici plutôt que recalculé dans `ReceiptDetailFragment` à partir de trois flux séparés, pour que le
 * Fragment reste un simple afficheur (voir [ReceiptDetailViewModel.detectAmountButtonState]).
 */
sealed interface DetectAmountButtonState {
    /** Un montant est déjà enregistré, OU une suggestion est déjà affichée par `suggestedAmountCard`
     * (jamais les deux invitations à la fois) — bouton totalement masqué. */
    data object Hidden : DetectAmountButtonState
    /** Rien n'est encore enregistré ni suggéré : bouton actionnable. */
    data object Idle : DetectAmountButtonState
    /** [ReceiptDetailViewModel.detectAmount] en cours (voir sa doc, peut prendre un temps notable) :
     * bouton visible mais désactivé, libellé différent. */
    data object Loading : DetectAmountButtonState
}

/**
 * ViewModel de "Détail du reçu" (cahier des charges "Gestion des reçus", section 6) : métadonnées,
 * aperçu de la première page du PDF, renommer/supprimer/partager/ouvrir avec une autre application.
 *
 * [ReceiptFileStorage]/[ReceiptPdfRenderer]/[ReceiptIntentLauncher]/[ReceiptTextExtractor] injectés
 * directement (pas de détour par [ReceiptRepository]) : même principe documenté sur
 * `ExternalAppLauncher` — ce sont des utilitaires techniques hors du domaine (Uri/Bitmap/Intent/texte
 * brut irréductibles), pas des capacités métier substituables. [ReceiptRepository] reste seul
 * responsable des métadonnées (Room). [ReceiptAmountParser], lui, est une fonction PURE (`object`,
 * pas de dépendance) — utilisé directement, sans injection (voir sa doc).
 */
@HiltViewModel
class ReceiptDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val receiptRepository: ReceiptRepository,
    private val receiptFileStorage: ReceiptFileStorage,
    private val receiptPdfRenderer: ReceiptPdfRenderer,
    private val receiptIntentLauncher: ReceiptIntentLauncher,
    private val receiptTextExtractor: ReceiptTextExtractor,
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

    private val _suggestedAmountMinor = MutableStateFlow<Long?>(null)

    /** Voir [detectAmount] : une SUGGESTION à confirmer, jamais écrite automatiquement dans
     * [Receipt.amountMinor] (voir la doc de tête de [ReceiptAmountParser]). Remise à `null` dès
     * qu'elle est traitée d'une façon ou d'une autre (voir [confirmSuggestedAmount]/[saveAmount]/
     * [dismissSuggestedAmount]) — ne reste jamais affichée après une décision de l'utilisateur. */
    val suggestedAmountMinor: StateFlow<Long?> = _suggestedAmountMinor.asStateFlow()

    private val _isDetectingAmount = MutableStateFlow(false)

    /** Voir [detectAmount] : l'extraction de texte peut prendre un temps notable sur un PDF
     * volumineux (contrairement au simple rendu de la page 1, voir [loadPreview]) — permet à
     * `ReceiptDetailFragment` d'afficher un indicateur de progression sur le bouton de déclenchement
     * (voir [DetectAmountButtonState.Loading]). */
    val isDetectingAmount: StateFlow<Boolean> = _isDetectingAmount.asStateFlow()

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

    /** Voir la doc de [DetectAmountButtonState] : combine [uiState]/[suggestedAmountMinor]/
     * [isDetectingAmount] en un seul état directement consommable par `ReceiptDetailFragment`. Doit
     * être déclaré APRÈS [uiState] (dépendance d'initialisation Kotlin : un `val` ne peut référencer
     * qu'un autre `val` déjà initialisé au-dessus de lui dans la classe). */
    val detectAmountButtonState: StateFlow<DetectAmountButtonState> =
        combine(uiState, suggestedAmountMinor, isDetectingAmount) { state, suggestion, isDetecting ->
            val receipt = (state as? AppResult.Success)?.data
            when {
                isDetecting -> DetectAmountButtonState.Loading
                receipt == null || receipt.amountMinor != null || suggestion != null -> DetectAmountButtonState.Hidden
                else -> DetectAmountButtonState.Idle
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = DetectAmountButtonState.Hidden
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

    /**
     * Lance l'extraction du texte du PDF puis l'heuristique de détection du montant (voir
     * [ReceiptTextExtractor]/[ReceiptAmountParser]) — jamais appelée automatiquement, uniquement
     * via `detectAmountButton` dans `ReceiptDetailFragment` (voir [DetectAmountButtonState]) : un
     * gros PDF peut rendre cette opération sensiblement plus lente qu'un simple rendu de page,
     * inutile de la lancer sans que l'utilisateur le demande explicitement. [_isDetectingAmount]
     * protège contre un double lancement (ex. double-tap) plutôt que d'annuler/relancer une
     * extraction déjà en cours.
     *
     * Résultat exposé via [suggestedAmountMinor] — `null` aussi bien si aucun texte n'a pu être
     * extrait (PDF scanné, protégé...) que si le texte extrait ne contient aucun montant reconnu
     * avec suffisamment de confiance : les deux cas sont indiscernables ici, volontairement (voir
     * la doc de [ReceiptTextExtractor.extractText]/[ReceiptAmountParser.parseAmount]).
     */
    fun detectAmount() {
        val receipt = (uiState.value as? AppResult.Success)?.data ?: return
        // `detectAmountButtonState` masque déjà le bouton pendant une extraction en cours (voir sa
        // doc) — cette garde reste un filet de sécurité pour un appel direct hors de ce bouton.
        if (_isDetectingAmount.value) return
        viewModelScope.launch {
            _isDetectingAmount.value = true
            _suggestedAmountMinor.value = withContext(ioDispatcher) {
                val file = receiptFileStorage.resolveFile(receipt.localPath)
                receiptTextExtractor.extractText(file)?.let { ReceiptAmountParser.parseAmount(it) }
            }
            _isDetectingAmount.value = false
        }
    }

    /** Accepte [suggestedAmountMinor] telle quelle — voir [saveAmount]. No-op si aucune suggestion
     * n'est actuellement affichée. */
    fun confirmSuggestedAmount() {
        val amount = _suggestedAmountMinor.value ?: return
        saveAmount(amount)
    }

    /**
     * Enregistre [amountMinor] dans [Receipt.amountMinor] — appelée aussi bien par
     * [confirmSuggestedAmount] (valeur suggérée acceptée telle quelle) que par la future action
     * "Modifier" de `ReceiptDetailFragment` (Étape 4, valeur corrigée manuellement par
     * l'utilisateur avant confirmation) : les DEUX cas restent une décision explicite de
     * l'utilisateur, jamais une écriture automatique — voir la doc de tête de [ReceiptAmountParser].
     */
    fun saveAmount(amountMinor: Long) {
        val current = (uiState.value as? AppResult.Success)?.data ?: return
        _suggestedAmountMinor.value = null
        viewModelScope.launch { receiptRepository.saveReceipt(current.copy(amountMinor = amountMinor)) }
    }

    /** Rejette [suggestedAmountMinor] sans rien enregistrer — l'utilisateur pourra relancer
     * [detectAmount] plus tard s'il change d'avis, rien n'est définitif. */
    fun dismissSuggestedAmount() {
        _suggestedAmountMinor.value = null
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
