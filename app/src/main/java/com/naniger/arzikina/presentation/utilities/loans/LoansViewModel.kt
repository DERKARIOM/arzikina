package com.naniger.arzikina.presentation.utilities.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.CurrencyAmount
import com.naniger.arzikina.domain.model.Loan
import com.naniger.arzikina.domain.model.LoanStatus
import com.naniger.arzikina.domain.model.LoanType
import com.naniger.arzikina.domain.model.isSettled
import com.naniger.arzikina.domain.model.liveStatus
import com.naniger.arzikina.domain.repository.AccountRepository
import com.naniger.arzikina.domain.repository.LoanRepository
import com.naniger.arzikina.domain.repository.PersonRepository
import com.naniger.arzikina.util.AppResult
import com.naniger.arzikina.util.Constants
import com.naniger.arzikina.util.technicalMessage
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
import javax.inject.Inject

/** Filtre par type, en plus du texte de recherche libre — même principe que
 * `TransactionTypeFilter` (écran Transactions). */
enum class LoanTypeFilterOption {
    ALL,
    LENT,
    BORROWED
}

/** Filtre par statut, indépendant du filtre par type — mêmes valeurs que [LoanStatus], plus [ALL]. */
enum class LoanStatusFilterOption {
    ALL,
    ONGOING,
    REPAID,
    OVERDUE,
    UPCOMING
}

/**
 * Filtres appliqués à la liste des prêts/emprunts (voir [LoansViewModel.uiState]) — même structure
 * que `TransactionFilters` (écran Transactions).
 */
data class LoanFilters(
    val query: String = "",
    val type: LoanTypeFilterOption = LoanTypeFilterOption.ALL,
    val status: LoanStatusFilterOption = LoanStatusFilterOption.ALL
) {
    /** Exclut volontairement [query] : le champ de recherche a déjà son propre bouton "effacer",
     * même raisonnement que `TransactionFilters.hasActiveFilters`. */
    val hasActiveFilters: Boolean
        get() = type != LoanTypeFilterOption.ALL || status != LoanStatusFilterOption.ALL

    /** Recherche OU filtre actif : la liste (et donc [LoansSummary]) ne montre qu'une partie des
     * prêts/emprunts. */
    val isNarrowed: Boolean
        get() = query.isNotBlank() || hasActiveFilters
}

/**
 * État affiché par l'écran principal Prêts/Emprunts.
 *
 * [summary] est calculé sur les SEULS prêts/emprunts affichés : "Total reçu"/"Total dû" et les
 * compteurs suivent la recherche et les filtres (même comportement que la page Web), et
 * [LoansSummary.isFiltered] le signale à l'écran. Sans recherche ni filtre, ce sont les totaux
 * de tous les prêts/emprunts, comme avant.
 */
data class LoansUiState(
    val summary: LoansSummary,
    val items: List<LoanListItem>
)

/**
 * État de l'écran principal Prêts/Emprunts : combine [LoanRepository] (prêts/emprunts),
 * [PersonRepository] (noms), [AccountRepository] (devise de chaque compte associé, voir
 * [LoansSummary]) et [filters] — même structure que [com.naniger.arzikina.presentation.accounts.AccountsViewModel]
 * pour les trois premiers, et que `TransactionsViewModel` pour le filtrage en mémoire.
 */
@HiltViewModel
class LoansViewModel @Inject constructor(
    loanRepository: LoanRepository,
    personRepository: PersonRepository,
    accountRepository: AccountRepository
) : ViewModel() {

    private val _filters = MutableStateFlow(LoanFilters())
    val filters: StateFlow<LoanFilters> = _filters.asStateFlow()

    val uiState: StateFlow<AppResult<LoansUiState>> = combine(
        loanRepository.observeLoans(),
        personRepository.observePersons(),
        accountRepository.observeAccounts(),
        _filters
    ) { loans, persons, accounts, filters ->
        val personNamesById = persons.associate { it.id to it.name }
        val accountsById = accounts.associateBy { it.id }
        val normalizedQuery = filters.query.trim()
        // Voir la doc de `computeLoanStatus` : recalculé à CHAQUE affichage plutôt que de faire
        // confiance à `Loan.status` (persisté, peut être périmé par le simple écoulement du temps
        // — ex. un prêt en retard depuis hier qui n'a connu aucune écriture depuis).
        val now = System.currentTimeMillis()
        val liveStatusById = loans.associate {
            it.id to it.liveStatus(now)
        }

        // Actifs (En cours/En retard/À venir) affichés avant les remboursés, comme sur la
        // maquette ; puis par échéance croissante — LoanDao fournit déjà cet ordre secondaire.
        // Chaque prêt garde son élément de liste : le résumé se calcule sur les MÊMES prêts que
        // ceux affichés, sans refaire le filtrage une seconde fois.
        val visible = loans
            .sortedBy { liveStatusById.getValue(it.id).isSettled }
            .map { loan -> loan to loan.toListItem(personNamesById, accountsById, liveStatusById.getValue(loan.id)) }
            .filter { (_, item) -> matchesType(item.type, filters.type) }
            .filter { (_, item) -> matchesStatus(item.status, filters.status) }
            .filter { (_, item) -> matchesQuery(item, normalizedQuery) }
        val visibleLoans = visible.map { (loan, _) -> loan }

        LoansUiState(
            // Voir la doc de LoansUiState.summary : calculé sur les prêts AFFICHÉS.
            summary = LoansSummary(
                totalReceivable = sumRemainingByCurrency(visibleLoans, LoanType.LENT, accountsById),
                lentCount = visibleLoans.count { it.type == LoanType.LENT },
                totalOwed = sumRemainingByCurrency(visibleLoans, LoanType.BORROWED, accountsById),
                borrowedCount = visibleLoans.count { it.type == LoanType.BORROWED },
                totalCount = visibleLoans.size,
                isFiltered = filters.isNarrowed
            ),
            items = visible.map { (_, item) -> item }
        )
    }
        .map<LoansUiState, AppResult<LoansUiState>> { AppResult.Success(it) }
        .catch { throwable -> emit(AppResult.Error(throwable.technicalMessage(), throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )

    fun onQueryChange(query: String) {
        _filters.update { it.copy(query = query) }
    }

    fun onTypeFilterChange(type: LoanTypeFilterOption) {
        _filters.update { it.copy(type = type) }
    }

    fun onStatusFilterChange(status: LoanStatusFilterOption) {
        _filters.update { it.copy(status = status) }
    }

    fun resetFilters() {
        _filters.update { it.copy(type = LoanTypeFilterOption.ALL, status = LoanStatusFilterOption.ALL) }
    }

    private fun matchesType(type: LoanType, filter: LoanTypeFilterOption): Boolean =
        when (filter) {
            LoanTypeFilterOption.ALL -> true
            LoanTypeFilterOption.LENT -> type == LoanType.LENT
            LoanTypeFilterOption.BORROWED -> type == LoanType.BORROWED
        }

    private fun matchesStatus(status: LoanStatus, filter: LoanStatusFilterOption): Boolean =
        when (filter) {
            LoanStatusFilterOption.ALL -> true
            LoanStatusFilterOption.ONGOING -> status == LoanStatus.ONGOING
            LoanStatusFilterOption.REPAID -> status == LoanStatus.REPAID
            LoanStatusFilterOption.OVERDUE -> status == LoanStatus.OVERDUE
            LoanStatusFilterOption.UPCOMING -> status == LoanStatus.UPCOMING
        }

    /**
     * Recherche libre sur le nom de la personne et le titre. [LoanListItem.title] est BRUT (voir
     * sa doc) : peut être vide si [Loan.description] l'est — dans ce cas, seul le nom de la
     * personne reste cherchable pour cet élément. Le libellé de repli générique ("Prêt"/"Emprunt",
     * voir [defaultLoanTitleRes]) affiché à sa place n'est PAS pris en compte ici : le résoudre
     * demanderait un accès aux ressources de chaînes localisées, que ce ViewModel (pur, sans
     * dépendance Android) n'a délibérément pas — simplification mineure, cohérente avec le reste de
     * la classe.
     */
    private fun matchesQuery(item: LoanListItem, query: String): Boolean {
        if (query.isEmpty()) return true
        return item.personName.contains(query, ignoreCase = true) || item.title.contains(query, ignoreCase = true)
    }

    private fun Loan.toListItem(
        personNamesById: Map<Long, String>,
        accountsById: Map<Long, Account>,
        liveStatus: LoanStatus
    ) = LoanListItem(
        id = id,
        personName = personNamesById[personId].orEmpty(),
        type = type,
        status = liveStatus,
        title = description,
        amountMinor = amount,
        amountRepaidMinor = amountRepaid,
        remainingAmountMinor = remainingAmount,
        currencyCode = accountsById[accountId]?.currencyCode ?: Constants.DEFAULT_CURRENCY_CODE,
        progressPercent = computeLoanProgressPercent(amountRepaid, amount)
    )

    /** Voir la doc de [LoansSummary] pour le raisonnement (regroupement par devise, pas de
     * conversion de change). Même technique que `DashboardViewModel.sumByAccountCurrency`. */
    private fun sumRemainingByCurrency(
        loans: List<Loan>,
        type: LoanType,
        accountsById: Map<Long, Account>
    ): List<CurrencyAmount> =
        loans
            .filter { it.type == type }
            .mapNotNull { loan -> accountsById[loan.accountId]?.let { it.currencyCode to loan.remainingAmount } }
            .groupBy({ it.first }, { it.second })
            .map { (currencyCode, amounts) -> CurrencyAmount(currencyCode, amounts.sum()) }
}
