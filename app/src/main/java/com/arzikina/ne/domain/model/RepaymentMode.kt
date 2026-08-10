package com.arzikina.ne.domain.model

/**
 * Mode de remboursement prévu pour un [Loan] — purement INDICATIF pour l'instant (affiché sur
 * l'écran de détail, voir cahier des charges section 10) : n'automatise ni rappels ni échéancier
 * de versements à cette étape. [CUSTOM] n'a pas de texte libre associé (contrairement à
 * [LoanReason.OTHER]) : le mode de remboursement réel se lit de toute façon dans l'historique des
 * remboursements ([LoanPayment]), ce champ ne fait qu'annoncer une INTENTION de départ.
 */
enum class RepaymentMode {
    SINGLE,
    INSTALLMENTS,
    MONTHLY,
    WEEKLY,
    CUSTOM
}
