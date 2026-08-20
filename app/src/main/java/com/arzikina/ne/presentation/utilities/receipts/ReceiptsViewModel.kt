package com.arzikina.ne.presentation.utilities.receipts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Receipt
import com.arzikina.ne.domain.repository.ReceiptRepository
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.DatePeriods
import com.arzikina.ne.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Événement ponctuel d'import manuel (cahier des charges section 12) — même schéma que
 * `presentation.settings.BackupEvent` : un [kotlinx.coroutines.flow.SharedFlow], jamais porté par
 * [ReceiptsViewModel.uiState] (qui ne doit refléter que l'état de la LISTE, pas un événement
 * ponctuel qui ne doit être consommé qu'une seule fois par la Fragment). */
sealed interface ReceiptImportEvent {
    data object Success : ReceiptImportEvent
    data class Failure(val message: String) : ReceiptImportEvent
}

/**
 * Filtre par période — cahier des charges "Gestion des reçus", section 9 : "Tous/Aujourd'hui/Cette
 * semaine/Ce mois". Type dédié plutôt qu'une réutilisation de
 * `presentation.transactions.TransactionPeriodFilter` : ce dernier n'a pas de valeur [TODAY]
 * (absente du filtre Transactions), et les deux fonctionnalités n'ont par ailleurs aucune raison
 * d'évoluer en bloc (voir `TransactionPeriodFilter`/`BudgetPeriod`, déjà des types distincts par
 * fonctionnalité dans ce projet).
 */
enum class ReceiptPeriodFilter {
    ALL,
    TODAY,
    THIS_WEEK,
    THIS_MONTH
}

/** Filtres appliqués à la liste des reçus — même schéma que `TransactionFilters`. */
data class ReceiptFilters(
    val query: String = "",
    val period: ReceiptPeriodFilter = ReceiptPeriodFilter.ALL
) {
    /** Exclut volontairement [query] : le champ de recherche a déjà son propre bouton "effacer". */
    val hasActiveFilters: Boolean
        get() = period != ReceiptPeriodFilter.ALL
}

/**
 * ViewModel de l'écran "Gestion des reçus" : liste groupée par jour ([ReceiptDaySection]), recherche
 * instantanée (nom/provenance/montant, cahier des charges section 9), filtre par période, et import
 * manuel (voir [importReceipt] — le partage entrant reste géré par
 * [com.arzikina.ne.MainActivity], hors navigation, voir sa doc). La suppression/le renommage restent
 * hors de ce ViewModel : voir "Détail du reçu" aux étapes suivantes (même séparation que
 * `TransactionsViewModel`/`TransactionFormViewModel`).
 *
 * Le filtrage se fait en mémoire après lecture de [ReceiptRepository.observeReceipts], comme le
 * reste de l'application (voir `TransactionsViewModel`) : pas de requête SQL dynamique à maintenir
 * pour chaque combinaison de filtres — un nombre de reçus par utilisateur qui ne justifie pas cette
 * complexité supplémentaire.
 */
@HiltViewModel
class ReceiptsViewModel @Inject constructor(
    private val receiptRepository: ReceiptRepository
) : ViewModel() {

    private val _filters = MutableStateFlow(ReceiptFilters())
    val filters: StateFlow<ReceiptFilters> = _filters.asStateFlow()

    private val _events = MutableSharedFlow<ReceiptImportEvent>()
    val events: SharedFlow<ReceiptImportEvent> = _events.asSharedFlow()

    val uiState: StateFlow<AppResult<List<ReceiptDaySection>>> = combine(
        receiptRepository.observeReceipts(),
        _filters
    ) { receipts, filters ->
        val today = LocalDate.now()
        val normalizedQuery = filters.query.trim()
        receipts
            .asSequence()
            .filter { receipt -> matchesPeriod(receipt.receivedAt, filters.period, today) }
            .filter { receipt -> matchesQuery(receipt, normalizedQuery) }
            .toList()
            .groupByDay()
    }
        .map<List<ReceiptDaySection>, AppResult<List<ReceiptDaySection>>> { AppResult.Success(it) }
        .catch { throwable -> emit(AppResult.Error(throwable.message ?: "Erreur inconnue", throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )

    fun onQueryChange(query: String) {
        _filters.update { it.copy(query = query) }
    }

    fun onPeriodFilterChange(period: ReceiptPeriodFilter) {
        _filters.update { it.copy(period = period) }
    }

    fun resetFilters() {
        _filters.update { it.copy(period = ReceiptPeriodFilter.ALL) }
    }

    /**
     * Import manuel (sélecteur de fichiers, cahier des charges section 12) — même point d'entrée
     * [ReceiptRepository.importReceipt] que le partage entrant (voir
     * [com.arzikina.ne.MainActivity]), mais `sourceApp`/`sourceName` toujours `null` ici : un import
     * manuel n'a, par nature, aucune application "source" au sens du partage (cahier des charges
     * section 3, "ne jamais inventer la provenance" — s'applique aussi à ce cas).
     *
     * @param fallbackDisplayName nom utilisé si [ReceiptRepository.resolveDisplayName] ne peut pas
     * déterminer le nom d'origine — fourni par l'appelant (voir [ReceiptsFragment]) : cette classe ne
     * connaît elle-même aucune ressource `strings.xml` (voir la doc de [ReceiptRepository]).
     */
    fun importReceipt(sourceUri: String, mimeType: String, fallbackDisplayName: String) {
        viewModelScope.launch {
            runCatching {
                val displayName = receiptRepository.resolveDisplayName(sourceUri) ?: fallbackDisplayName
                receiptRepository.importReceipt(sourceUri, displayName, mimeType, sourceApp = null, sourceName = null)
            }
                .onSuccess { _events.emit(ReceiptImportEvent.Success) }
                .onFailure { _events.emit(ReceiptImportEvent.Failure(it.message ?: "Erreur inconnue")) }
        }
    }

    private fun matchesPeriod(receivedAt: Long, filter: ReceiptPeriodFilter, today: LocalDate): Boolean =
        when (filter) {
            ReceiptPeriodFilter.ALL -> true
            ReceiptPeriodFilter.TODAY -> DatePeriods.toLocalDate(receivedAt) == today
            ReceiptPeriodFilter.THIS_WEEK -> DatePeriods.isInCurrentWeek(receivedAt, today)
            ReceiptPeriodFilter.THIS_MONTH -> DatePeriods.isInCurrentMonth(receivedAt, today)
        }

    /**
     * Recherche locale (cahier des charges section 9) : nom, provenance, ET montant si disponible
     * (voir [Receipt.amountMinor] — `null` dans cette première version, voir sa doc — la recherche
     * par montant fonctionnera donc automatiquement dès qu'une future extraction viendra le
     * renseigner, sans modification nécessaire ici).
     */
    private fun matchesQuery(receipt: Receipt, query: String): Boolean {
        if (query.isEmpty()) return true
        return receipt.fileName.contains(query, ignoreCase = true) ||
            receipt.sourceName?.contains(query, ignoreCase = true) == true ||
            receipt.amountMinor?.let { Money.formatAmount(it).contains(query, ignoreCase = true) } == true
    }
}
