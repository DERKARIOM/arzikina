package com.arzikina.ne.presentation.utilities.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.PaymentMethod
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.CategoryRepository
import com.arzikina.ne.domain.repository.RecurringTransactionRepository
import com.arzikina.ne.domain.repository.TransactionRepository
import com.arzikina.ne.presentation.accounts.computeCurrentBalances
import com.arzikina.ne.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * État du dialogue de validation en file d'attente (voir [RecurringOccurrenceQueueDialogFragment]) :
 * un instantané FIGÉ des occurrences `PENDING` au moment de l'ouverture du dialogue — contrairement
 * à `RecurringTransactionsViewModel.uiState`, PAS un flux réactif recalculé à chaque changement de la
 * base. Ce choix est délibéré : faire disparaître/réordonner des lignes sous les yeux de
 * l'utilisateur pendant qu'il traite une file l'un après l'autre serait déroutant, alors qu'un
 * instantané avancé localement à chaque action ([items] perd juste sa tête à chaque
 * [RecurringOccurrenceQueueViewModel.accept]/[RecurringOccurrenceQueueViewModel.reject]/
 * [RecurringOccurrenceQueueViewModel.confirmEdit]) donne une progression stable et prévisible
 * ("2 sur 5" ne recule/avance jamais de façon inattendue).
 *
 * [totalCount] fixe (taille de l'instantané initial) sert de dénominateur à la progression affichée
 * ("X sur [totalCount]") — ne change jamais après [RecurringOccurrenceQueueViewModel.init],
 * contrairement à [items] qui se vide progressivement.
 *
 * [editState] non-null = le Fragment doit afficher le formulaire d'édition de [currentItem] plutôt
 * que son résumé en lecture — voir [RecurringOccurrenceQueueViewModel.startEdit].
 */
data class RecurringOccurrenceQueueUiState(
    val items: List<RecurringOccurrenceUiItem> = emptyList(),
    val totalCount: Int = 0,
    val isProcessing: Boolean = false,
    val editState: OccurrenceEditState? = null
) {
    val currentItem: RecurringOccurrenceUiItem? get() = items.firstOrNull()
    val currentPosition: Int get() = (totalCount - items.size + 1).coerceAtLeast(1)
}

/**
 * Valeurs ÉDITABLES d'une occurrence en cours de modification (voir "Modifier" du cahier des
 * charges) — pré-remplies depuis la règle d'origine au moment de [RecurringOccurrenceQueueViewModel.startEdit],
 * puis envoyées telles quelles à `RecurringTransactionRepository.acceptOccurrenceWithChanges` sur
 * [RecurringOccurrenceQueueViewModel.confirmEdit] : la règle d'origine n'est JAMAIS modifiée par ce
 * chemin (voir la doc de cette méthode), uniquement la transaction ponctuelle créée pour cette
 * occurrence.
 */
data class OccurrenceEditState(
    val type: TransactionType,
    val amountInput: String,
    val accountId: Long,
    val categoryId: Long,
    val description: String,
    val paymentMethod: PaymentMethod?,
    val date: Long,
    val amountError: String? = null,
    val accountError: String? = null,
    val categoryError: String? = null
)

sealed interface RecurringOccurrenceQueueEvent {
    /** File vide (dès l'ouverture, ou après le dernier
     * [RecurringOccurrenceQueueViewModel.accept]/[RecurringOccurrenceQueueViewModel.reject]/
     * [RecurringOccurrenceQueueViewModel.confirmEdit]) : le Fragment doit se fermer. */
    data object Dismiss : RecurringOccurrenceQueueEvent
}

@HiltViewModel
class RecurringOccurrenceQueueViewModel @Inject constructor(
    private val recurringTransactionRepository: RecurringTransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecurringOccurrenceQueueUiState())
    val uiState: StateFlow<RecurringOccurrenceQueueUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RecurringOccurrenceQueueEvent>()
    val events: SharedFlow<RecurringOccurrenceQueueEvent> = _events.asSharedFlow()

    /** Pour le sélecteur de compte du formulaire d'édition (voir [startEdit]) — même principe que
     * `RecurringTransactionFormViewModel.accounts`. */
    val accounts: StateFlow<List<Account>> = accountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Recalculée à chaque changement de type dans le formulaire d'édition (voir [onEditTypeChange])
     * — `emptyList()` tant qu'aucune édition n'est en cours ([OccurrenceEditState.type] alors
     * `null`), même principe que `RecurringTransactionFormViewModel.categories`. */
    val categories: StateFlow<List<Category>> = _uiState
        .map { it.editState?.type }
        .distinctUntilChanged()
        .flatMapLatest { type -> if (type == null) flowOf(emptyList()) else categoryRepository.observeCategoriesByType(type) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Solde COURANT de chaque compte, pour le sélecteur de compte du formulaire d'édition (voir
     * `AccountPickerDialog`/`TransactionFormFragment.accountBalances`, même principe) — jamais
     * [Account.initialBalance] seul, qui ignore les transactions déjà enregistrées. */
    val accountBalances: StateFlow<Map<Long, Long>> = combine(
        accounts,
        transactionRepository.observeTransactions()
    ) { accounts, transactions -> computeCurrentBalances(accounts, transactions) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch {
            val pending = recurringTransactionRepository.observePendingOccurrences().first()
            if (pending.isEmpty()) {
                // Peut arriver si tout a déjà été traité entre le moment où MainActivity a décidé
                // d'afficher ce dialogue et celui où cet instantané est pris (fenêtre très courte) :
                // se ferme immédiatement plutôt que d'afficher une file vide.
                _events.emit(RecurringOccurrenceQueueEvent.Dismiss)
                return@launch
            }

            val rulesById = recurringTransactionRepository.observeRecurringTransactions().first().associateBy { it.id }
            // Lecture ponctuelle indépendante de [accounts] (voir sa doc) : cette propriété
            // n'utilise `SharingStarted.WhileSubscribed` qu'à partir du premier collecteur du
            // Fragment, qui n'a pas encore démarré à ce stade de `init` — `.value` y vaudrait encore
            // `emptyList()`.
            val accountsById = accountRepository.observeAccounts().first().associateBy { it.id }
            val categoriesById = categoryRepository.observeCategories().first().associateBy { it.id }

            val items = pending
                .sortedBy { it.scheduledDate }
                .mapNotNull { occurrence -> occurrence.toUiItem(rulesById, accountsById, categoriesById) }

            _uiState.update { it.copy(items = items, totalCount = items.size) }
        }
    }

    /** "Enregistrer" (voir cahier des charges) : crée la transaction à partir des valeurs actuelles
     * de la règle, sans modification ponctuelle. */
    fun accept() {
        val occurrenceId = _uiState.value.currentItem?.occurrenceId ?: return
        performAction { recurringTransactionRepository.acceptOccurrence(occurrenceId) }
    }

    /** "Rejeter" (voir cahier des charges) : confirmation déjà obtenue côté Fragment avant cet
     * appel (voir `ConfirmDialogs`, action définitive). */
    fun reject() {
        val occurrenceId = _uiState.value.currentItem?.occurrenceId ?: return
        performAction { recurringTransactionRepository.rejectOccurrence(occurrenceId) }
    }

    /** "Modifier" (voir cahier des charges) : bascule l'affichage vers le formulaire d'édition,
     * pré-rempli avec les valeurs ACTUELLES de la règle (pas celles d'une éventuelle occurrence déjà
     * modifiée précédemment, qui n'existe pas encore ici — [currentItem] est toujours `PENDING`). */
    fun startEdit() {
        val item = _uiState.value.currentItem ?: return
        val rule = item.recurringTransaction
        _uiState.update {
            it.copy(
                editState = OccurrenceEditState(
                    type = rule.type,
                    amountInput = Money.formatMajorUnits(rule.amount),
                    accountId = rule.accountId,
                    categoryId = rule.categoryId ?: 0L,
                    description = rule.description,
                    paymentMethod = rule.paymentMethod,
                    date = item.scheduledDate
                )
            )
        }
    }

    /** Retour au résumé en lecture, sans rien enregistrer — [currentItem] reste inchangé, toujours
     * `PENDING`. */
    fun cancelEdit() {
        _uiState.update { it.copy(editState = null) }
    }

    fun onEditTypeChange(type: TransactionType) {
        // Même raisonnement que `RecurringTransactionFormViewModel.onTypeChange` : une catégorie de
        // dépense n'a pas de sens pour un revenu (et inversement).
        updateEditState { it.copy(type = type, categoryId = 0L, categoryError = null) }
    }

    fun onEditAmountChange(value: String) {
        updateEditState { it.copy(amountInput = value, amountError = null) }
    }

    fun onEditAccountChange(accountId: Long) {
        updateEditState { it.copy(accountId = accountId, accountError = null) }
    }

    fun onEditCategoryChange(categoryId: Long) {
        updateEditState { it.copy(categoryId = categoryId, categoryError = null) }
    }

    fun onEditDescriptionChange(value: String) {
        updateEditState { it.copy(description = value) }
    }

    fun onEditPaymentMethodChange(method: PaymentMethod?) {
        updateEditState { it.copy(paymentMethod = method) }
    }

    fun onEditDateChange(millis: Long) {
        updateEditState { it.copy(date = millis) }
    }

    /** "Confirmer" le formulaire d'édition : valide puis crée la transaction via
     * `RecurringTransactionRepository.acceptOccurrenceWithChanges` (voir sa doc — la règle d'origine
     * n'est jamais modifiée par ce chemin) et avance la file, exactement comme [accept]/[reject]. */
    fun confirmEdit() {
        val state = _uiState.value
        val edit = state.editState ?: return
        val occurrenceId = state.currentItem?.occurrenceId ?: return

        val amountMinor = Money.parseToMinorUnits(edit.amountInput)
        val amountValid = amountMinor != null && amountMinor > 0L
        val accountValid = edit.accountId != 0L
        val categoryValid = edit.categoryId != 0L

        // Messages en dur (pas de `Context` dans ce ViewModel) : même convention que
        // `RecurringTransactionFormViewModel.save`/`TransactionFormViewModel.save`.
        if (!amountValid || !accountValid || !categoryValid) {
            updateEditState {
                it.copy(
                    amountError = if (!amountValid) "Montant invalide" else null,
                    accountError = if (!accountValid) "Choisis un compte" else null,
                    categoryError = if (!categoryValid) "Choisis une catégorie" else null
                )
            }
            return
        }

        performAction {
            recurringTransactionRepository.acceptOccurrenceWithChanges(
                occurrenceId = occurrenceId,
                type = edit.type,
                amount = amountMinor!!,
                accountId = edit.accountId,
                categoryId = edit.categoryId,
                date = edit.date,
                description = edit.description,
                paymentMethod = edit.paymentMethod
            )
        }
    }

    private fun updateEditState(transform: (OccurrenceEditState) -> OccurrenceEditState) {
        _uiState.update { state -> state.copy(editState = state.editState?.let(transform)) }
    }

    /**
     * Exécute l'action puis avance TOUJOURS la file d'un élément, même en cas d'échec (ex.
     * occurrence déjà traitée depuis une autre session — cas de course improbable mais possible,
     * voir `RecurringTransactionRepository.pendingOccurrenceOrThrow`) : mieux vaut passer à
     * l'élément suivant que de bloquer l'utilisateur sur un état incohérent qu'il ne peut pas
     * résoudre depuis ce dialogue. Referme aussi systématiquement [OccurrenceEditState] : l'élément
     * suivant démarre toujours en lecture, jamais dans l'état d'édition de l'élément précédent.
     */
    private fun performAction(action: suspend () -> Unit) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            runCatching { action() }
            val remaining = _uiState.value.items.drop(1)
            _uiState.update { it.copy(items = remaining, isProcessing = false, editState = null) }
            if (remaining.isEmpty()) {
                _events.emit(RecurringOccurrenceQueueEvent.Dismiss)
            }
        }
    }
}
