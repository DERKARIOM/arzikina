package com.arzikina.ne.presentation.utilities.loans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Loan
import com.arzikina.ne.domain.model.LoanPayment
import com.arzikina.ne.domain.model.computeLoanStatus
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.LoanRepository
import com.arzikina.ne.domain.repository.PersonRepository
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * État affiché par l'écran "Détail du prêt/emprunt".
 *
 * [payments] : UNIQUEMENT les remboursements réellement enregistrés (voir
 * [LoanRepository.observePayments]), du plus récent au plus ancien. La maquette fournie affiche
 * en plus un ratio "Nombre de versements: 2/3" et une ligne "En attente" fictive, qui supposent un
 * échéancier persisté — un tel échéancier n'existe pas dans le modèle de données actuel
 * ([Loan.repaymentMode] n'a d'ailleurs aucun sélecteur dédié, voir sa doc) et ne fait pas partie du
 * plan de développement Prêts/Emprunts (l'Étape "Gestion des remboursements" suit celle-ci). Cet
 * écran reste donc fidèle aux données réelles plutôt que d'inventer un versement à venir.
 *
 * @param accountNamesById TOUS les comptes (pas seulement [Loan.accountId]) : un [LoanPayment]
 * peut être réglé sur un compte différent de celui utilisé à la création du prêt/emprunt (voir la
 * doc de [LoanPayment.accountId]) — nécessaire pour afficher le bon nom de compte sur chaque ligne
 * de la section "Versements" (cahier des charges section 11).
 */
data class LoanDetailUiState(
    val loan: Loan,
    val personName: String,
    val currencyCode: String,
    val payments: List<LoanPayment>,
    val accountNamesById: Map<Long, String>
)

@HiltViewModel
class LoanDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loanRepository: LoanRepository,
    personRepository: PersonRepository,
    accountRepository: AccountRepository
) : ViewModel() {

    val loanId: Long = savedStateHandle.get<Long>(LOAN_ID_ARG) ?: 0L

    val uiState: StateFlow<AppResult<LoanDetailUiState>> = combine(
        loanRepository.observeLoans(),
        personRepository.observePersons(),
        accountRepository.observeAccounts(),
        loanRepository.observePayments(loanId)
    ) { loans, persons, accounts, payments ->
        val storedLoan = loans.find { it.id == loanId } ?: return@combine null
        // Voir la doc de `computeLoanStatus` : recalculé à l'affichage plutôt que de faire
        // confiance à `Loan.status` persisté, qui peut être périmé par le simple écoulement du temps.
        val loan = storedLoan.copy(
            status = computeLoanStatus(
                amount = storedLoan.amount,
                amountRepaid = storedLoan.amountRepaid,
                startDate = storedLoan.startDate,
                dueDate = storedLoan.dueDate,
                nowEpochMillis = System.currentTimeMillis()
            )
        )
        LoanDetailUiState(
            loan = loan,
            personName = persons.find { it.id == loan.personId }?.name.orEmpty(),
            currencyCode = accounts.find { it.id == loan.accountId }?.currencyCode ?: Constants.DEFAULT_CURRENCY_CODE,
            payments = payments,
            accountNamesById = accounts.associate { it.id to it.name }
        )
    }
        .map<LoanDetailUiState?, AppResult<LoanDetailUiState>> { state ->
            state?.let { AppResult.Success(it) } ?: AppResult.Error("Prêt/emprunt introuvable")
        }
        .catch { throwable -> emit(AppResult.Error(throwable.message ?: "Erreur inconnue", throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )

    fun deleteLoan() {
        viewModelScope.launch {
            loanRepository.deleteLoan(loanId)
        }
    }

    /** Voir la doc de [LoanRepository.deletePayment] : supprime aussi, atomiquement, la
     * transaction Arzikina liée et recalcule [Loan.amountRepaid]/[Loan.remainingAmount]/
     * [Loan.status] — [uiState] se met à jour automatiquement (il observe déjà [LoanRepository.observePayments]
     * et [LoanRepository.observeLoans]), aucun rechargement explicite nécessaire ici. */
    fun deletePayment(paymentId: Long) {
        viewModelScope.launch {
            loanRepository.deletePayment(paymentId)
        }
    }

    private companion object {
        const val LOAN_ID_ARG = "loanId"
    }
}
