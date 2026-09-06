package com.arzikina.ne.presentation.utilities.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.CategoryRepository
import com.arzikina.ne.domain.repository.RecurringTransactionRepository
import com.arzikina.ne.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * État de l'écran "Transactions planifiées" : les trois sections déjà résolues et jointes à leur
 * compte/catégorie (voir [RecurringOccurrenceUiItem]), prêtes pour [RecurringTransactionsAdapter] —
 * même principe que `LoansUiState`/`DashboardUiState`.
 */
data class RecurringTransactionsUiState(
    val summary: RecurringTransactionsSummary,
    val pendingItems: List<RecurringOccurrenceUiItem>,
    val upcomingItems: List<RecurringOccurrenceUiItem>,
    val historyItems: List<RecurringOccurrenceUiItem>
)

@HiltViewModel
class RecurringTransactionsViewModel @Inject constructor(
    private val recurringTransactionRepository: RecurringTransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    /** Occurrences dont Valider/Rejeter est en cours (voir [accept]/[reject]) — garde-fou anti
     *  double-tap, purement en mémoire (jamais persisté) : un id qui y reste bloqué après un crash
     *  de processus redevient simplement cliquable au redémarrage, aucun état à nettoyer. */
    private val _pendingActionIds = MutableStateFlow<Set<Long>>(emptySet())
    val pendingActionIds: StateFlow<Set<Long>> = _pendingActionIds.asStateFlow()

    val uiState: StateFlow<AppResult<RecurringTransactionsUiState>> = combine(
        recurringTransactionRepository.observeRecurringTransactions(),
        recurringTransactionRepository.observePendingOccurrences(),
        recurringTransactionRepository.observeProcessedOccurrences(),
        accountRepository.observeAccounts(),
        categoryRepository.observeCategories()
    ) { rules, pending, processed, accounts, categories ->
        val rulesById = rules.associateBy { it.id }
        val accountsById = accounts.associateBy { it.id }
        val categoriesById = categories.associateBy { it.id }

        val pendingItems = pending
            .sortedBy { it.scheduledDate }
            .mapNotNull { occurrence -> occurrence.toUiItem(rulesById, accountsById, categoriesById) }

        // "À venir" = la PROCHAINE échéance connue de chaque règle active (`nextExecutionDate`,
        // déjà avancée dès qu'une occurrence PENDING a été générée pour la date précédente — voir
        // `RecurringTransactionRepository.generateMissingOccurrences`) : une règle dont l'échéance
        // du jour est déjà "À traiter" apparaît donc légitimement AUSSI ici, avec sa vraie échéance
        // SUIVANTE (pas une redite de la même date) — comportement voulu, pas un doublon.
        // `pendingKeys` reste une garde défensive pure (règle, date) plutôt qu'une simple date du
        // jour : couvre la brève fenêtre où l'app vient de démarrer et n'a pas encore fini de
        // générer les occurrences du jour (voir `MainActivity.generateMissingRecurringOccurrences`),
        // où `nextExecutionDate` pointerait encore sur une date déjà due mais pas encore PENDING.
        val pendingKeys = pending.map { it.recurringTransactionId to it.scheduledDate }.toSet()
        val upcomingItems = rules
            .filter { it.isActive && (it.id to it.nextExecutionDate) !in pendingKeys }
            .sortedBy { it.nextExecutionDate }
            .map { rule ->
                RecurringOccurrenceUiItem(
                    occurrenceId = null,
                    recurringTransaction = rule,
                    account = accountsById[rule.accountId],
                    category = rule.categoryId?.let { categoriesById[it] },
                    scheduledDate = rule.nextExecutionDate,
                    status = null
                )
            }

        val historyItems = processed
            .sortedByDescending { it.processedAt }
            .mapNotNull { occurrence -> occurrence.toUiItem(rulesById, accountsById, categoriesById) }

        RecurringTransactionsUiState(
            summary = RecurringTransactionsSummary(pendingCount = pendingItems.size, upcomingCount = upcomingItems.size),
            pendingItems = pendingItems,
            upcomingItems = upcomingItems,
            historyItems = historyItems
        )
    }
        .map<RecurringTransactionsUiState, AppResult<RecurringTransactionsUiState>> { AppResult.Success(it) }
        .catch { throwable -> emit(AppResult.Error(throwable.message ?: "Erreur inconnue", throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )

    /**
     * "Valider" directement depuis la ligne "À traiter" (voir cahier des charges "Supprimer le
     * dialogue au lancement") : crée la transaction à partir des valeurs ACTUELLES de la règle, sans
     * modification ponctuelle — même appel que l'ancien bouton "Enregistrer" de la file d'attente
     * supprimée (voir `RecurringTransactionRepository.acceptOccurrence`). "Modifier" avant validation
     * reste possible via [RecurringOccurrenceEditDialogFragment], ouvert par un tap sur la ligne
     * elle-même plutôt que ce bouton.
     */
    fun accept(occurrenceId: Long) = performAction(occurrenceId) {
        recurringTransactionRepository.acceptOccurrence(occurrenceId)
    }

    /** "Rejeter" — confirmation déjà obtenue côté Fragment avant cet appel (voir `ConfirmDialogs`,
     *  action définitive, même principe que l'ancienne file d'attente supprimée). */
    fun reject(occurrenceId: Long) = performAction(occurrenceId) {
        recurringTransactionRepository.rejectOccurrence(occurrenceId)
    }

    /** Garde-fou anti double-tap (voir [_pendingActionIds]) : ignore un second appel pour le même
     *  [occurrenceId] tant que le premier n'est pas terminé, échec compris. */
    private fun performAction(occurrenceId: Long, action: suspend () -> Unit) {
        if (occurrenceId in _pendingActionIds.value) return
        _pendingActionIds.update { it + occurrenceId }
        viewModelScope.launch {
            runCatching { action() }
            _pendingActionIds.update { it - occurrenceId }
        }
    }
}
