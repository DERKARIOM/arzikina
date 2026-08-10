package com.arzikina.ne.domain.model

/**
 * Un remboursement (reçu si [Loan.type] vaut [LoanType.LENT], versé s'il vaut
 * [LoanType.BORROWED]) enregistré contre un [Loan].
 *
 * [accountId] est INDÉPENDANT de [Loan.accountId] : un remboursement peut être reçu/versé sur un
 * compte différent de celui utilisé à la création du prêt (ex. prêt accordé depuis "Espèces",
 * remboursement reçu sur "Compte bancaire") — voir cahier des charges section 11, qui affiche le
 * compte utilisé pour CHAQUE remboursement individuellement.
 *
 * @param transactionId id de la [Transaction] Arzikina générée automatiquement pour ce
 * remboursement (voir la doc de [Loan], section synchronisation) — TOUJOURS renseigné : un
 * [LoanPayment] n'existe que si sa transaction a été créée avec succès dans la même opération
 * (voir `LoanRepository.recordPayment`, qui garantit les deux écritures ensemble). Utilisé pour
 * supprimer proprement la transaction correspondante si ce remboursement est un jour annulé.
 * @param note libre, vide par défaut (voir cahier des charges section 12, "note facultative").
 * @param id 0L tant que le remboursement n'a pas encore été enregistré en base.
 */
data class LoanPayment(
    val id: Long = 0L,
    val loanId: Long,
    val accountId: Long,
    val amount: Long,
    val date: Long,
    val note: String = "",
    val transactionId: Long,
    val createdAt: Long
)
