package com.arzikina.ne.presentation.utilities.recurring

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.PaymentMethod
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.model.combineDayAndTime
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
 * Valeurs ÉDITABLES d'une occurrence `PENDING` en cours de modification (voir cahier des charges
 * "Garder Modifier en action secondaire") — pré-remplies depuis la règle d'origine ET l'heure de
 * déclenchement configurée (voir [combineDayAndTime], même correctif que
 * `RecurringTransactionRepositoryImpl.acceptOccurrence`) au moment de
 * [RecurringOccurrenceEditViewModel.init], puis envoyées telles quelles à
 * `RecurringTransactionRepository.acceptOccurrenceWithChanges` sur
 * [RecurringOccurrenceEditViewModel.confirm] : la règle d'origine n'est JAMAIS modifiée par ce
 * chemin, uniquement la transaction ponctuelle créée pour cette occurrence.
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

/**
 * `edit` reste `null` tant que l'occurrence n'a pas fini de charger (voir
 * [RecurringOccurrenceEditViewModel.init]) — le Fragment n'affiche rien tant que `null`, pas d'état
 * de chargement dédié : la lecture est quasi instantanée (données déjà en cache Room).
 */
data class RecurringOccurrenceEditUiState(
    val edit: OccurrenceEditState? = null,
    val isProcessing: Boolean = false
)

sealed interface RecurringOccurrenceEditEvent {
    /** Occurrence introuvable/déjà traitée à l'ouverture, ou modification confirmée avec succès (ou
     *  échec — voir [RecurringOccurrenceEditViewModel.confirm], même philosophie de robustesse que
     *  l'ancienne file d'attente : mieux vaut fermer proprement qu'un dialogue bloqué). */
    data object Dismiss : RecurringOccurrenceEditEvent
}

/**
 * Édite puis valide UNE SEULE occurrence `PENDING` précise (voir cahier des charges "Supprimer le
 * dialogue au lancement" — remplace l'ancienne file d'attente `RecurringOccurrenceQueueViewModel`,
 * ouverte automatiquement au démarrage). Ouvert désormais UNIQUEMENT sur tap explicite d'une ligne
 * "À traiter" dans `RecurringTransactionsFragment` (voir sa doc "Modifier en action secondaire") —
 * Valider/Rejeter SANS modification passent directement par
 * `RecurringTransactionsViewModel.accept`/`reject`, sans ouvrir cet écran.
 *
 * [occurrenceId] lu depuis [SavedStateHandle] (voir [ARG_OCCURRENCE_ID]) — même convention que
 * `AccountDetailViewModel.accountId` : `RecurringOccurrenceEditDialogFragment` définit
 * `arguments = bundleOf(ARG_OCCURRENCE_ID to occurrenceId)` avant `.show(...)`, lu automatiquement
 * ici par le mécanisme standard `by viewModels()`/`SavedStateViewModelFactory`, aucun lien avec la
 * Navigation Component (ce dialogue reste affiché via `FragmentManager`, pas `NavController`).
 */
@HiltViewModel
class RecurringOccurrenceEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recurringTransactionRepository: RecurringTransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository
) : ViewModel() {

    private val occurrenceId: Long = savedStateHandle.get<Long>(ARG_OCCURRENCE_ID) ?: 0L

    private val _uiState = MutableStateFlow(RecurringOccurrenceEditUiState())
    val uiState: StateFlow<RecurringOccurrenceEditUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RecurringOccurrenceEditEvent>()
    val events: SharedFlow<RecurringOccurrenceEditEvent> = _events.asSharedFlow()

    /** Pour le sélecteur de compte du formulaire — même principe que
     * `RecurringTransactionFormViewModel.accounts`. */
    val accounts: StateFlow<List<Account>> = accountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Recalculée à chaque changement de type — même principe que l'ancienne
     * `RecurringOccurrenceQueueViewModel.categories`. */
    val categories: StateFlow<List<Category>> = _uiState
        .map { it.edit?.type }
        .distinctUntilChanged()
        .flatMapLatest { type -> if (type == null) flowOf(emptyList()) else categoryRepository.observeCategoriesByType(type) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Solde COURANT de chaque compte — jamais [Account.initialBalance] seul, même principe que
     * `TransactionFormFragment.accountBalances`. */
    val accountBalances: StateFlow<Map<Long, Long>> = combine(
        accounts,
        transactionRepository.observeTransactions()
    ) { accounts, transactions -> computeCurrentBalances(accounts, transactions) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch {
            val occurrence = recurringTransactionRepository.observePendingOccurrences().first()
                .firstOrNull { it.id == occurrenceId }
            val rule = occurrence?.let { recurringTransactionRepository.getRecurringTransaction(it.recurringTransactionId) }
            if (occurrence == null || rule == null) {
                // Traitée entre-temps depuis un autre appareil, ou règle supprimée — rien à éditer.
                _events.emit(RecurringOccurrenceEditEvent.Dismiss)
                return@launch
            }
            _uiState.update {
                it.copy(
                    edit = OccurrenceEditState(
                        type = rule.type,
                        amountInput = Money.formatForInput(rule.amount),
                        accountId = rule.accountId,
                        categoryId = rule.categoryId ?: 0L,
                        description = rule.description,
                        paymentMethod = rule.paymentMethod,
                        // Même correctif que `RecurringTransactionRepositoryImpl.acceptOccurrence` :
                        // l'heure configurée sur la règle, jamais minuit brut.
                        date = combineDayAndTime(occurrence.scheduledDate, rule.triggerHour, rule.triggerMinute)
                    )
                )
            }
        }
    }

    fun onTypeChange(type: TransactionType) {
        // Une catégorie de dépense n'a pas de sens pour un revenu (et inversement) — même
        // raisonnement que `RecurringTransactionFormViewModel.onTypeChange`.
        updateEdit { it.copy(type = type, categoryId = 0L, categoryError = null) }
    }

    fun onAmountChange(value: String) {
        updateEdit { it.copy(amountInput = value, amountError = null) }
    }

    fun onAccountChange(accountId: Long) {
        updateEdit { it.copy(accountId = accountId, accountError = null) }
    }

    fun onCategoryChange(categoryId: Long) {
        updateEdit { it.copy(categoryId = categoryId, categoryError = null) }
    }

    fun onDescriptionChange(value: String) {
        updateEdit { it.copy(description = value) }
    }

    fun onPaymentMethodChange(method: PaymentMethod?) {
        updateEdit { it.copy(paymentMethod = method) }
    }

    fun onDateChange(millis: Long) {
        updateEdit { it.copy(date = millis) }
    }

    /** "Confirmer" : crée la transaction via `RecurringTransactionRepository.acceptOccurrenceWithChanges`
     * (la règle d'origine n'est jamais modifiée) puis ferme le dialogue. */
    fun confirm() {
        val edit = _uiState.value.edit ?: return
        if (_uiState.value.isProcessing) return

        val amountMinor = Money.parseToMinorUnits(edit.amountInput)
        val amountValid = amountMinor != null && amountMinor > 0L
        val accountValid = edit.accountId != 0L
        val categoryValid = edit.categoryId != 0L

        // Messages en dur (pas de `Context` dans ce ViewModel) : même convention que
        // `RecurringTransactionFormViewModel.save`/l'ancienne `RecurringOccurrenceQueueViewModel`.
        if (!amountValid || !accountValid || !categoryValid) {
            updateEdit {
                it.copy(
                    amountError = if (!amountValid) "Montant invalide" else null,
                    accountError = if (!accountValid) "Choisis un compte" else null,
                    categoryError = if (!categoryValid) "Choisis une catégorie" else null
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            runCatching {
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
            _uiState.update { it.copy(isProcessing = false) }
            _events.emit(RecurringOccurrenceEditEvent.Dismiss)
        }
    }

    private fun updateEdit(transform: (OccurrenceEditState) -> OccurrenceEditState) {
        _uiState.update { state -> state.copy(edit = state.edit?.let(transform)) }
    }

    companion object {
        const val ARG_OCCURRENCE_ID = "occurrenceId"
    }
}
