package com.arzikina.ne.domain.model

/**
 * Motif d'un [Loan]. [OTHER] s'accompagne d'un texte libre (voir [Loan.reasonCustomText],
 * "permettre également de saisir un motif personnalisé") — mêmes principe que [PaymentMethod]
 * pour la séparation enum fermé/texte libre, mais ici le texte libre n'est utile QUE pour
 * [OTHER] (pas de texte affichable directement dans le domaine, voir
 * `presentation/utilities/loans` pour le mapping vers un libellé, à venir).
 */
enum class LoanReason {
    FINANCIAL_HELP,
    EDUCATION,
    HEALTH,
    PURCHASE,
    PROJECT,
    EMERGENCY,
    OTHER
}
