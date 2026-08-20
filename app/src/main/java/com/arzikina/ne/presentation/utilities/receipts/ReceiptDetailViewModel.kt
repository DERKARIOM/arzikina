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
import com.arzikina.ne.domain.model.FeeCategoryNames
import com.arzikina.ne.domain.model.LoanCategoryNames
import com.arzikina.ne.domain.model.Receipt
import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.CategoryRepository
import com.arzikina.ne.domain.repository.ReceiptRepository
import com.arzikina.ne.domain.repository.TransactionRepository
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.ReceiptAmountParser
import com.arzikina.ne.util.ReceiptTransactionInfo
import com.arzikina.ne.util.ReceiptTransactionInfoParser
import com.arzikina.ne.util.ReceiptTransactionMatcher
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
import kotlinx.coroutines.flow.first
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

    /** Voir [ReceiptDetailViewModel.onAddTransactionClicked] : ce reçu a déjà une transaction liée
     * (anti-doublon, voir [TransactionRepository.findByReceiptId], lecture ponctuelle AUTORITATIVE
     * au moment du clic — jamais celle, potentiellement périmée, de [ReceiptDetailViewModel.linkedTransaction])
     * — `ReceiptDetailFragment` navigue directement vers sa modification plutôt que d'en créer une
     * deuxième. */
    data class OpenLinkedTransaction(val transactionId: Long) : ReceiptDetailEvent

    /** Voir [ReceiptDetailViewModel.onAddTransactionClicked] : aucune transaction liée pour
     * l'instant — `ReceiptDetailFragment` navigue vers la création, préremplie avec [prefill]
     * (cahier des charges "Créer une transaction depuis un reçu"). */
    data class PrefillNewTransaction(val prefill: TransactionPrefill) : ReceiptDetailEvent
}

/**
 * Données préremplies transmises à `transactionFormFragment` (voir `nav_graph.xml`, chaque
 * argument `presetXxx`) — un simple regroupement, PAS un nouveau modèle de domaine : reflète
 * exactement les arguments de navigation disponibles (voir [TransactionFormViewModel.applyReceiptPresets]).
 * Chaque champ `null` indépendamment signifie "non détecté avec assez de confiance", jamais une
 * valeur inventée (voir [ReceiptTransactionInfoParser]/[ReceiptTransactionMatcher]).
 */
data class TransactionPrefill(
    val amountMinor: Long?,
    val feeAmountMinor: Long?,
    val dateTimeMillis: Long?,
    val description: String?,
    val categoryId: Long?,
    val accountId: Long?,
    val type: TransactionType?,
    val receiptId: Long
)

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
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
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

    /**
     * Transaction déjà créée depuis CE reçu (voir [Transaction.receiptId]), s'il y en a une —
     * dérivée de [TransactionRepository.observeTransactions] (déjà chargé ailleurs dans
     * l'application pour les soldes de comptes, voir `TransactionFormViewModel.accountBalances`)
     * plutôt que d'observer une nouvelle requête dédiée. Pilote UNIQUEMENT le libellé du bouton
     * "Ajouter comme transaction"/"Voir la transaction" (voir `ReceiptDetailFragment`) — la
     * décision de navigation elle-même revient à [TransactionRepository.findByReceiptId] (lecture
     * ponctuelle AUTORITATIVE au moment du clic, voir [onAddTransactionClicked]), jamais cette
     * valeur potentiellement microscopiquement périmée entre deux émissions.
     */
    val linkedTransaction: StateFlow<Transaction?> = transactionRepository.observeTransactions()
        .map { transactions -> transactions.find { it.receiptId == receiptId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), initialValue = null)

    private val _isPreparingTransaction = MutableStateFlow(false)

    /** Voir [onAddTransactionClicked] : extraction du texte + correspondance compte/catégorie, avant
     * navigation — peut prendre un temps notable sur un gros PDF (même raisonnement que
     * [isDetectingAmount]) : permet à `ReceiptDetailFragment` de désactiver le bouton et d'afficher
     * un libellé de progression plutôt que de laisser l'utilisateur taper plusieurs fois. */
    val isPreparingTransaction: StateFlow<Boolean> = _isPreparingTransaction.asStateFlow()

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

    /**
     * Bouton "Ajouter comme transaction" (cahier des charges "Créer une transaction depuis un
     * reçu") : vérifie D'ABORD l'anti-doublon ([TransactionRepository.findByReceiptId], lecture
     * ponctuelle AUTORITATIVE — voir [ReceiptDetailEvent.OpenLinkedTransaction]), puis seulement si
     * aucune transaction n'existe déjà, extrait le texte du reçu ([ReceiptTextExtractor]) et
     * l'analyse ([ReceiptTransactionInfoParser]) pour proposer une correspondance de compte/
     * catégorie ([ReceiptTransactionMatcher]) avant de laisser `ReceiptDetailFragment` naviguer vers
     * le formulaire préRempli ([ReceiptDetailEvent.PrefillNewTransaction]).
     *
     * [_isPreparingTransaction] protège contre un double lancement (double-tap), même principe que
     * [detectAmount]/[_isDetectingAmount].
     */
    fun onAddTransactionClicked() {
        val receipt = (uiState.value as? AppResult.Success)?.data ?: return
        if (_isPreparingTransaction.value) return

        viewModelScope.launch {
            _isPreparingTransaction.value = true
            val existing = transactionRepository.findByReceiptId(receiptId)
            if (existing != null) {
                _isPreparingTransaction.value = false
                _events.emit(ReceiptDetailEvent.OpenLinkedTransaction(existing.id))
                return@launch
            }

            val text = withContext(ioDispatcher) {
                val file = receiptFileStorage.resolveFile(receipt.localPath)
                receiptTextExtractor.extractText(file)
            }
            // Toujours `ReceiptTransactionInfo()` (tous les champs `null`) si le texte n'a pas pu
            // être extrait — jamais une erreur bloquante : le formulaire s'ouvre alors simplement
            // vide, exactement comme une création manuelle (voir la doc de tête de
            // ReceiptTransactionInfoParser, "ne jamais inventer").
            val info = text?.let { ReceiptTransactionInfoParser.parse(it) } ?: ReceiptTransactionInfo()

            // Correspondance de compte : dépend de Receipt.sourceApp, PAS du texte extrait — reste
            // tentée même si l'extraction de texte a échoué ci-dessus (voir ReceiptTransactionMatcher).
            val accounts = accountRepository.observeAccounts().first()
            val matchedAccount = ReceiptTransactionMatcher.matchAccountBySourceApp(accounts, receipt.sourceApp)

            // Catégorie du type DÉTECTÉ, ou EXPENSE par défaut (même valeur par défaut que
            // TransactionFormState.type, voir TransactionFormViewModel) : cohérent avec le type
            // affiché par le formulaire si presetType n'a lui-même pas pu être détecté. Exclut les
            // catégories système (Prêts/Emprunts, Frais et commissions), jamais choisies
            // manuellement — même filtrage que TransactionFormViewModel.categories, sans quoi le
            // mot "Frais" présent sur presque tout reçu Mobile Money pourrait faire correspondre à
            // tort la catégorie système "Frais et commissions" à la transaction PRINCIPALE.
            val categoryType = info.transactionType ?: TransactionType.EXPENSE
            val categories = categoryRepository.observeCategoriesByType(categoryType).first()
                .filterNot { it.name in LoanCategoryNames.ALL || it.name in FeeCategoryNames.ALL }
            val matchedCategory = ReceiptTransactionMatcher.matchCategoryByKeyword(categories, text)

            _isPreparingTransaction.value = false
            _events.emit(
                ReceiptDetailEvent.PrefillNewTransaction(
                    TransactionPrefill(
                        amountMinor = info.amountMinor,
                        feeAmountMinor = info.feeMinor,
                        dateTimeMillis = info.dateTimeMillis,
                        description = info.description,
                        categoryId = matchedCategory?.id,
                        accountId = matchedAccount?.id,
                        type = info.transactionType,
                        receiptId = receiptId
                    )
                )
            )
        }
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
