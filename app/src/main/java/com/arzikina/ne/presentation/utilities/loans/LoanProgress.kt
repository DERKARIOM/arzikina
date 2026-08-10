package com.arzikina.ne.presentation.utilities.loans

/**
 * Progression du remboursement, en pourcentage entier 0..100 (voir `item_loan.xml`,
 * `LinearProgressIndicator`). `0` si [amount] est nul ou négatif (donnée invalide, ne devrait pas
 * arriver — [com.arzikina.ne.domain.model.Loan.amount] est toujours positif) plutôt qu'une
 * division par zéro.
 */
fun computeLoanProgressPercent(amountRepaid: Long, amount: Long): Int {
    if (amount <= 0L) return 0
    return ((amountRepaid * 100) / amount).coerceIn(0L, 100L).toInt()
}
