package com.naniger.arzikina.domain.repository

import com.naniger.arzikina.domain.model.Loan
import com.naniger.arzikina.domain.model.LoanPayment
import kotlinx.coroutines.flow.Flow

/**
 * Contrat d'accès aux données des prêts/emprunts (voir [Loan]/[LoanPayment]).
 * Voir [AccountRepository] pour le raisonnement derrière cette séparation.
 *
 * Chaque écriture qui affecte l'argent réel de l'utilisateur ([saveLoan] pour un nouveau prêt,
 * [recordPayment], [deleteLoan], [deletePayment]) génère/supprime aussi, dans la MÊME opération
 * atomique, la [com.naniger.arzikina.domain.model.Transaction] Arzikina correspondante — voir la doc de
 * [Loan.transactionId]/[LoanPayment.transactionId]. Le contrat ne l'expose pas explicitement
 * (l'appelant n'a jamais besoin de le savoir), mais c'est un invariant fort de cette interface.
 *
 * Un prêt/emprunt transformé en cadeau ([convertToGift]) est VERROUILLÉ : [recordPayment],
 * [updatePayment], [deletePayment] et toute modification de son montant/compte/personne/type par [saveLoan] lèvent
 * [com.naniger.arzikina.domain.model.LoanGiftException.Locked]. Seule la date peut encore changer.
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
     * @throws com.naniger.arzikina.domain.model.LoanGiftException.Locked si le prêt/emprunt a été
     * transformé en cadeau.
     */
    suspend fun recordPayment(payment: LoanPayment): Long

    /**
     * Modifie un remboursement déjà enregistré (compte, montant, date et heure, note) : met à jour
     * la ligne [LoanPayment], sa transaction Arzikina liée (même compte, montant, date, description)
     * et recalcule [Loan.amountRepaid]/[Loan.remainingAmount]/[Loan.status] — atomiquement.
     * [LoanPayment.loanId], [LoanPayment.transactionId] et [LoanPayment.createdAt] ne changent jamais.
     *
     * Implémentation par défaut uniquement pour les doubles de test qui n'en ont pas besoin :
     * toute implémentation réelle DOIT la redéfinir.
     *
     * @throws IllegalStateException si le remboursement ou son prêt/emprunt n'existe pas, ou si le
     * nouveau montant dépasse le solde restant (en comptant le montant actuel de ce remboursement).
     * @throws com.naniger.arzikina.domain.model.LoanGiftException.Locked si le prêt/emprunt a été
     * transformé en cadeau.
     */
    suspend fun updatePayment(payment: LoanPayment) {
        throw UnsupportedOperationException("updatePayment n'est pas pris en charge par cette implémentation.")
    }

    /**
     * Annule un remboursement : supprime la transaction Arzikina liée et la ligne [LoanPayment],
     * et recalcule [Loan.amountRepaid]/[Loan.remainingAmount]/[Loan.status] — atomiquement.
     *
     * @throws com.naniger.arzikina.domain.model.LoanGiftException.Locked si le prêt/emprunt a été
     * transformé en cadeau.
     */
    suspend fun deletePayment(id: Long)

    /**
     * `null` si [transactionId] n'est celle d'AUCUN prêt/emprunt (transaction normale) — sinon
     * l'id du prêt/emprunt concerné, que [transactionId] soit son décaissement OU l'un de ses
     * remboursements (voir la doc de [Loan.transactionId]/[LoanPayment.transactionId]), OU sa
     * transaction cadeau (voir [Loan.giftTransactionId]).
     *
     * Utilisé par [com.naniger.arzikina.presentation.transactions.TransactionFormViewModel] pour
     * empêcher l'édition/suppression directe d'une transaction générée automatiquement par cette
     * fonctionnalité : la modifier hors de "Détail du prêt/emprunt" désynchroniserait
     * [Loan.amountRepaid]/[Loan.remainingAmount]/[Loan.status] du montant réellement enregistré.
     */
    suspend fun findLoanIdForTransaction(transactionId: Long): Long?

    /**
     * « Transformer en cadeau » : reclasse la part NON remboursée du prêt/emprunt [loanId] en
     * catégorie « Cadeaux » (dépense pour un prêt, revenu pour un emprunt) et le clôture
     * ([com.naniger.arzikina.domain.model.LoanStatus.GIFTED]) — atomiquement, sans AUCUN nouveau
     * mouvement d'argent (voir `domain/model/LoanGift.kt` pour la règle comptable complète) :
     * - rien de remboursé : la transaction de décaissement est reclassée sur place ;
     * - remboursement partiel : le décaissement est réduit à la part remboursée et UNE transaction
     *   cadeau est créée pour le reste, même compte, même date que le prêt/emprunt.
     * Le prêt/emprunt n'est jamais supprimé ; son montant d'origine et ses remboursements restent
     * intacts (traçabilité).
     *
     * @param description texte de la transaction cadeau (« Cadeau à Abdou »), construit par la
     * présentation dans la langue de l'utilisateur.
     * @return id de la transaction cadeau.
     * @throws com.naniger.arzikina.domain.model.LoanGiftException.NotConvertible si la transformation
     * n'est plus permise (voir `canConvertToGift`).
     */
    suspend fun convertToGift(loanId: Long, description: String): Long
}
