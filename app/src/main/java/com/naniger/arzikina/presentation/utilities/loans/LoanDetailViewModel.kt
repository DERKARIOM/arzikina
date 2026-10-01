package com.naniger.arzikina.presentation.utilities.loans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.Loan
import com.naniger.arzikina.domain.model.LoanPayment
import com.naniger.arzikina.domain.model.liveStatus
import com.naniger.arzikina.domain.repository.AccountRepository
import com.naniger.arzikina.domain.repository.LoanRepository
import com.naniger.arzikina.domain.repository.PersonRepository
import com.naniger.arzikina.util.AppResult
import com.naniger.arzikina.util.Constants
import com.naniger.arzikina.util.LoanDateTime
import com.naniger.arzikina.util.technicalMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
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
 * @param accountsById TOUS les comptes (pas seulement [Loan.accountId]) : un [LoanPayment]
 * peut être réglé sur un compte différent de celui utilisé à la création du prêt/emprunt (voir la
 * doc de [LoanPayment.accountId]) — nécessaire pour afficher le bon nom de compte sur chaque ligne
 * de la section "Versements" (cahier des charges section 11).
 */
data class LoanDetailUiState(
    val loan: Loan,
    val personName: String,
    val currencyCode: String,
    val payments: List<LoanPayment>,
    val accountsById: Map<Long, Account>
)

/** Refus de [LoanDetailViewModel.updateStartDateTime] — voir sa doc. */
sealed interface LoanDetailEvent {
    /** Le début doit tomber un jour AVANT l'échéance (même règle que la création). */
    data object StartNotBeforeDue : LoanDetailEvent

    /** Le début ne peut pas être postérieur (jour) à un versement déjà enregistré. */
    data object StartAfterPayment : LoanDetailEvent
}

@HiltViewModel
class LoanDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loanRepository: LoanRepository,
    personRepository: PersonRepository,
    accountRepository: AccountRepository
) : ViewModel() {

    val loanId: Long = LoanDetailFragmentArgs.fromSavedStateHandle(savedStateHandle).loanId

    val uiState: StateFlow<AppResult<LoanDetailUiState>> = combine(
        loanRepository.observeLoans(),
        personRepository.observePersons(),
        accountRepository.observeAccounts(),
        loanRepository.observePayments(loanId)
    ) { loans, persons, accounts, payments ->
        val storedLoan = loans.find { it.id == loanId } ?: return@combine null
        // Voir la doc de `computeLoanStatus` : recalculé à l'affichage plutôt que de faire
        // confiance à `Loan.status` persisté, qui peut être périmé par le simple écoulement du temps.
        val loan = storedLoan.copy(status = storedLoan.liveStatus(System.currentTimeMillis()))
        LoanDetailUiState(
            loan = loan,
            personName = persons.find { it.id == loan.personId }?.name.orEmpty(),
            currencyCode = accounts.find { it.id == loan.accountId }?.currencyCode ?: Constants.DEFAULT_CURRENCY_CODE,
            payments = payments,
            accountsById = accounts.associateBy { it.id }
        )
    }
        .map<LoanDetailUiState?, AppResult<LoanDetailUiState>> { state ->
            state?.let { AppResult.Success(it) } ?: AppResult.Error("Loan not found")
        }
        .catch { throwable -> emit(AppResult.Error(throwable.technicalMessage(), throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )

    private val _events = MutableSharedFlow<LoanDetailEvent>()
    val events: SharedFlow<LoanDetailEvent> = _events.asSharedFlow()

    /**
     * « Modifier la date et l'heure » : SEULE modification possible d'un prêt/emprunt existant.
     * Ne change que [Loan.startDate] (date + heure) : montant, compte, personne, échéance,
     * remboursements et statut financier restent identiques. Passe par
     * [LoanRepository.saveLoan] (branche mise à jour), qui réaligne la transaction de décaissement
     * sur la même date/heure et enfile la synchronisation des deux — même instant partout.
     *
     * Mêmes règles, par JOUR, qu'à la création (voir LoanFormViewModel) : début avant l'échéance,
     * jamais après un versement déjà enregistré.
     */
    fun updateStartDateTime(newStartMillis: Long) {
        viewModelScope.launch {
            val stored = loanRepository.getLoan(loanId) ?: return@launch
            if (!LoanDateTime.isBeforeDay(newStartMillis, stored.dueDate)) {
                _events.emit(LoanDetailEvent.StartNotBeforeDue)
                return@launch
            }
            val payments = (uiState.value as? AppResult.Success)?.data?.payments.orEmpty()
            if (payments.any { LoanDateTime.isBeforeDay(it.date, newStartMillis) }) {
                _events.emit(LoanDetailEvent.StartAfterPayment)
                return@launch
            }
            if (stored.startDate == newStartMillis) return@launch
            loanRepository.saveLoan(stored.copy(startDate = newStartMillis))
        }
    }

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
}
