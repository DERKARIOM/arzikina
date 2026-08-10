package com.arzikina.ne.domain.repository

import com.arzikina.ne.domain.model.Loan
import com.arzikina.ne.domain.model.LoanPayment
import kotlinx.coroutines.flow.Flow

/**
 * Contrat d'accès aux données des prêts/emprunts (voir [Loan]/[LoanPayment]).
 * Voir [AccountRepository] pour le raisonnement derrière cette séparation.
 *
 * Chaque écriture qui affecte l'argent réel de l'utilisateur ([saveLoan] pour un nouveau prêt,
 * [recordPayment], [deleteLoan], [deletePayment]) génère/supprime aussi, dans la MÊME opération
 * atomique, la [com.arzikina.ne.domain.model.Transaction] Arzikina correspondante — voir la doc de
 * [Loan.transactionId]/[LoanPayment.transactionId]. Le contrat ne l'expose pas explicitement
 * (l'appelant n'a jamais besoin de le savoir), mais c'est un invariant fort de cette interface.
 */
interface LoanRepository {

    /** Flux réactif de tous les prêts/emprunts, triés par échéance. */
    fun observeLoans(): Flow<List<Loan>>

    suspend fun getLoan(id: Long): Loan?

    /** Flux réactif des remboursements d'un prêt/emprunt, du plus récent au plus ancien. */
    fun observePayments(loanId: Long): Flow<List<LoanPayment>>

    /**
     * Si [Loan.id] vaut 0 : crée le prêt/emprunt ET sa transaction de décaissement initial
     * (voir [Loan.transactionId]) — [Loan.amountRepaid]/[Loan.remainingAmount]/[Loan.status] sont
     * TOUJOURS recalculés ici, quelles que soient les valeurs fournies par l'appelant.
     *
     * Si [Loan.id] est non nul : met à jour les champs modifiables (personne, compte, montant,
     * échéance, raison, mode de remboursement, description...) SANS toucher à l'historique des
     * remboursements déjà enregistrés ni à la transaction de décaissement déjà créée — ces deux
     * éléments sont uniquement gérés par [recordPayment]/[deletePayment].
     *
     * Retourne l'id définitif du prêt/emprunt.
     */
    suspend fun saveLoan(loan: Loan): Long

    /**
     * Supprime le prêt/emprunt, son historique de remboursements (cascade SQLite, voir
     * `data/local/entity/LoanEntity`), ET toutes les transactions Arzikina liées (décaissement +
     * remboursements) — nettoyage atomique, voir la doc de cette interface.
     */
    suspend fun deleteLoan(id: Long)

    /**
     * Enregistre un remboursement : crée la transaction Arzikina correspondante, la ligne
     * [LoanPayment], et met à jour [Loan.amountRepaid]/[Loan.remainingAmount]/[Loan.status] —
     * atomiquement. Retourne l'id définitif du remboursement.
     *
     * @throws IllegalStateException si le prêt/emprunt n'existe pas, ou si [LoanPayment.amount]
     * dépasse le solde restant du prêt/emprunt.
     */
    suspend fun recordPayment(payment: LoanPayment): Long

    /**
     * Annule un remboursement : supprime la transaction Arzikina liée et la ligne [LoanPayment],
     * et recalcule [Loan.amountRepaid]/[Loan.remainingAmount]/[Loan.status] — atomiquement.
     */
    suspend fun deletePayment(id: Long)

    /**
     * `null` si [transactionId] n'est celle d'AUCUN prêt/emprunt (transaction normale) — sinon
     * l'id du prêt/emprunt concerné, que [transactionId] soit son décaissement OU l'un de ses
     * remboursements (voir la doc de [Loan.transactionId]/[LoanPayment.transactionId]).
     *
     * Utilisé par [com.arzikina.ne.presentation.transactions.TransactionFormViewModel] pour
     * empêcher l'édition/suppression directe d'une transaction générée automatiquement par cette
     * fonctionnalité : la modifier hors de "Détail du prêt/emprunt" désynchroniserait
     * [Loan.amountRepaid]/[Loan.remainingAmount]/[Loan.status] du montant réellement enregistré.
     */
    suspend fun findLoanIdForTransaction(transactionId: Long): Long?
}
