package com.arzikina.ne.presentation.utilities.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.domain.model.Loan
import com.arzikina.ne.domain.model.LoanStatus
import com.arzikina.ne.domain.model.LoanType
import com.arzikina.ne.domain.model.computeLoanStatus
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.LoanRepository
import com.arzikina.ne.domain.repository.PersonRepository
import com.arzikina.ne.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Une part de la répartition par statut (voir [LoanStatisticsUiState.statusBreakdown]). */
data class LoanStatusBreakdownItem(
    val status: LoanStatus,
    val count: Int,
    val percentage: Float
)

/**
 * Solde net d'une personne, tous prêts/emprunts confondus (voir [LoanStatisticsUiState.personBalances]).
 *
 * @param netAmountMinor `remainingAmount` cumulé des prêts LENT à cette personne MOINS celui des
 * emprunts BORROWED auprès d'elle — positif si elle vous doit globalement de l'argent, négatif si
 * c'est l'inverse. Jamais zéro dans la liste (voir [LoanStatisticsViewModel.computePersonBalances],
 * une personne totalement soldée des deux côtés n'a rien à montrer ici).
 */
data class LoanPersonBalanceItem(
    val personName: String,
    val currencyCode: String,
    val netAmountMinor: Long
)

data class LoanStatisticsUiState(
    /** Cumul historique (jamais net des remboursements) des montants prêtés/empruntés — voir la
     * doc de [LoanStatisticsViewModel] pour la distinction avec les totaux "restants" de
     * [LoansSummary] (écran principal). */
    val totalLent: List<CurrencyAmount>,
    val totalBorrowed: List<CurrencyAmount>,
    val totalRepaidReceived: List<CurrencyAmount>,
    val totalRepaidPaid: List<CurrencyAmount>,
    val statusBreakdown: List<LoanStatusBreakdownItem>,
    val personBalances: List<LoanPersonBalanceItem>
)

/**
 * État de l'écran "Statistiques" de la fonctionnalité Prêts/Emprunts, atteint depuis l'action
 * dédiée de la Toolbar de [LoansFragment] — même structure `combine`/[AppResult] que
 * `com.arzikina.ne.presentation.statistics.StatisticsViewModel` (écran Statistiques général).
 *
 * Distinction avec [LoansSummary] (cartes de l'écran principal) : celui-ci montre les montants
 * RESTANTS (`remainingAmount`, ce qu'il reste concrètement à régler) ; cet écran montre en plus
 * les montants HISTORIQUES cumulés (`amount`/`amountRepaid`, jamais net) — deux lectures
 * complémentaires, pas redondantes : "combien j'ai prêté/emprunté au total dans ma vie" vs
 * "combien reste dû aujourd'hui".
 */
@HiltViewModel
class LoanStatisticsViewModel @Inject constructor(
    loanRepository: LoanRepository,
    personRepository: PersonRepository,
    accountRepository: AccountRepository
) : ViewModel() {

    val uiState: StateFlow<AppResult<LoanStatisticsUiState>> = combine(
        loanRepository.observeLoans(),
        personRepository.observePersons(),
        accountRepository.observeAccounts()
    ) { loans, persons, accounts ->
        val accountsById = accounts.associateBy { it.id }
        val personNamesById = persons.associate { it.id to it.name }

        LoanStatisticsUiState(
            totalLent = sumByCurrency(loans, accountsById, { it.type == LoanType.LENT }, { it.amount }),
            totalBorrowed = sumByCurrency(loans, accountsById, { it.type == LoanType.BORROWED }, { it.amount }),
            totalRepaidReceived = sumByCurrency(loans, accountsById, { it.type == LoanType.LENT }, { it.amountRepaid }),
            totalRepaidPaid = sumByCurrency(loans, accountsById, { it.type == LoanType.BORROWED }, { it.amountRepaid }),
            statusBreakdown = computeStatusBreakdown(loans),
            personBalances = computePersonBalances(loans, personNamesById, accountsById)
        )
    }
        .map<LoanStatisticsUiState, AppResult<LoanStatisticsUiState>> { AppResult.Success(it) }
        .catch { throwable -> emit(AppResult.Error(throwable.message ?: "Erreur inconnue", throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )

    /** [count]/[totalCount] plutôt qu'un ordre fixe : les statuts absents (aucun prêt/emprunt dans
     * cet état) sont omis, comme [computeCategoryBreakdown] omet les catégories sans dépense sur
     * l'écran Statistiques général. Statut recalculé via [computeLoanStatus] (voir sa doc) plutôt
     * que [Loan.status] persisté, qui peut être périmé par le simple écoulement du temps — sinon
     * un prêt en retard depuis longtemps continuerait à compter comme "En cours" ici. */
    private fun computeStatusBreakdown(loans: List<Loan>): List<LoanStatusBreakdownItem> {
        val totalCount = loans.size
        if (totalCount == 0) return emptyList()
        val now = System.currentTimeMillis()
        return loans
            .groupBy { computeLoanStatus(it.amount, it.amountRepaid, it.startDate, it.dueDate, now) }
            .map { (status, group) -> LoanStatusBreakdownItem(status, group.size, group.size.toFloat() / totalCount.toFloat()) }
            .sortedByDescending { it.count }
    }

    /** Voir la doc de [LoanPersonBalanceItem]. Regroupé par (personne, devise) — même raisonnement
     * que [LoansSummary], une personne peut avoir des prêts/emprunts dans plusieurs devises. */
    private fun computePersonBalances(
        loans: List<Loan>,
        personNamesById: Map<Long, String>,
        accountsById: Map<Long, Account>
    ): List<LoanPersonBalanceItem> =
        loans
            .mapNotNull { loan -> accountsById[loan.accountId]?.let { account -> Triple(loan, account.currencyCode, loan.remainingAmount) } }
            .groupBy { (loan, currencyCode, _) -> loan.personId to currencyCode }
            .mapNotNull { (key, group) ->
                val (personId, currencyCode) = key
                val net = group.sumOf { (loan, _, remaining) -> if (loan.type == LoanType.LENT) remaining else -remaining }
                if (net == 0L) return@mapNotNull null
                LoanPersonBalanceItem(
                    personName = personNamesById[personId].orEmpty(),
                    currencyCode = currencyCode,
                    netAmountMinor = net
                )
            }
            .sortedByDescending { kotlin.math.abs(it.netAmountMinor) }

    /** Regroupe [loans] filtrés par [predicate] par devise (via le compte associé), en additionnant
     * le champ choisi par [amountSelector] — voir [LoansViewModel.sumRemainingByCurrency] pour le
     * même raisonnement, généralisé ici pour s'appliquer aussi bien à `Loan.amount` (montant
     * historique) qu'à `Loan.amountRepaid` (déjà remboursé). */
    private fun sumByCurrency(
        loans: List<Loan>,
        accountsById: Map<Long, Account>,
        predicate: (Loan) -> Boolean,
        amountSelector: (Loan) -> Long
    ): List<CurrencyAmount> =
        loans
            .filter(predicate)
            .mapNotNull { loan -> accountsById[loan.accountId]?.let { it.currencyCode to amountSelector(loan) } }
            .groupBy({ it.first }, { it.second })
            .map { (currencyCode, amounts) -> CurrencyAmount(currencyCode, amounts.sum()) }
}
