package com.naniger.arzikina.util

/**
 * Progression d'un compte « Objectif d'épargne » (`AccountType.SAVINGS_GOAL`), prête à afficher —
 * voir [SavingsGoalProgress.of]. Tous les montants sont en unité mineure.
 *
 * @property balance solde RÉEL du compte (peut être négatif ; jamais retouché).
 * @property target montant cible (toujours > 0).
 * @property saved montant considéré comme épargné : le solde, jamais négatif.
 * @property remaining reste à épargner, jamais négatif (0 dès que l'objectif est atteint).
 * @property exceededBy dépassement de l'objectif (0 tant qu'il n'est pas dépassé).
 * @property percent pourcentage AFFICHÉ, borné à 0..100 — règle Arzikina : on n'affiche jamais
 * « 120 % », la barre est pleine et [isExceeded] signale le dépassement (voir [exceededBy]).
 */
data class SavingsGoalSnapshot(
    val balance: Long,
    val target: Long,
    val saved: Long,
    val remaining: Long,
    val exceededBy: Long,
    val percent: Int,
    val isReached: Boolean
) {
    val isExceeded: Boolean get() = exceededBy > 0L
}

/**
 * Calculs d'un objectif d'épargne, en Kotlin pur (sans Android) pour être testés unitairement
 * (voir `SavingsGoalProgressTest`). Même principe que [FinancialPlanProgress] et [BudgetProgress] :
 * uniquement AFFICHÉS, jamais stockés en base, recalculés à chaque émission — la progression suit
 * donc automatiquement le solde du compte (transactions, transferts, synchronisation).
 *
 * Tous les montants sont en unité mineure (voir [Money.MINOR_UNITS_PER_MAJOR]).
 */
object SavingsGoalProgress {

    /**
     * Progression (%) = solde / cible × 100, arrondie à l'entier INFÉRIEUR (on n'affiche jamais
     * 100 % avant que la cible soit réellement atteinte) et bornée à 0..100. `0` pour une cible
     * nulle ou négative (objectif mal défini : aucune division par zéro).
     */
    fun progressPercent(currentAmount: Long, targetAmount: Long): Int {
        if (targetAmount <= 0L) return 0
        val saved = currentAmount.coerceAtLeast(0L)
        // Comparaison AVANT multiplication : `saved * 100` pourrait déborder pour un solde énorme.
        if (saved >= targetAmount) return 100
        return ((saved * 100) / targetAmount).toInt()
    }

    fun isCompleted(currentAmount: Long, targetAmount: Long): Boolean =
        targetAmount > 0L && currentAmount >= targetAmount

    fun remainingAmount(currentAmount: Long, targetAmount: Long): Long =
        (targetAmount - currentAmount.coerceAtLeast(0L)).coerceAtLeast(0L)

    /**
     * Situation complète de l'objectif pour un compte de solde [balance] et de cible [target].
     * `null` si [target] est absent ou ≤ 0 (compte qui n'est pas — ou plus — un objectif valide) :
     * l'appelant n'affiche alors simplement aucune progression.
     */
    fun of(balance: Long, target: Long?): SavingsGoalSnapshot? {
        if (target == null || target <= 0L) return null
        val saved = balance.coerceAtLeast(0L)
        return SavingsGoalSnapshot(
            balance = balance,
            target = target,
            saved = saved,
            remaining = remainingAmount(balance, target),
            exceededBy = (saved - target).coerceAtLeast(0L),
            percent = progressPercent(balance, target),
            isReached = isCompleted(balance, target)
        )
    }
}
