package com.naniger.arzikina.util

import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

/**
 * Situation de l'échéance d'un objectif d'épargne, prête à afficher (voir [SavingsGoalProgress.deadlineOf]).
 */
sealed interface SavingsGoalDeadline {
    /** Aucune échéance définie. */
    data object None : SavingsGoalDeadline

    /** Objectif atteint : l'échéance n'a plus d'importance. */
    data object Reached : SavingsGoalDeadline

    /** [isSoon] : échéance dans [SavingsGoalProgress.DUE_SOON_DAYS] jours ou moins (mise en avant). */
    data class Remaining(val date: LocalDate, val days: Int, val isSoon: Boolean) : SavingsGoalDeadline

    data class Today(val date: LocalDate) : SavingsGoalDeadline

    data class Overdue(val date: LocalDate) : SavingsGoalDeadline
}

/** Montant à épargner pour tenir l'échéance (voir [SavingsGoalProgress.suggestionOf]), en unité mineure. */
sealed interface SavingsSuggestion {
    /** Au moins un mois avant l'échéance : montant par mois. */
    data class PerMonth(val amountMinor: Long) : SavingsSuggestion

    /** Moins d'un mois avant l'échéance : tout le reste, d'ici l'échéance. */
    data class BeforeDeadline(val amountMinor: Long) : SavingsSuggestion
}

/**
 * Calculs d'un objectif d'épargne, en Kotlin pur (sans Android) pour être testés unitairement
 * (voir `SavingsGoalProgressTest`). Même principe que [FinancialPlanProgress] et [BudgetProgress] :
 * uniquement AFFICHÉS, jamais stockés en base, recalculés à chaque émission.
 *
 * Tous les montants sont en unité mineure (voir [Money.MINOR_UNITS_PER_MAJOR]).
 */
object SavingsGoalProgress {

    /** En dessous de ce nombre de jours, l'échéance est mise en avant (puce orange). */
    const val DUE_SOON_DAYS = 30

    /** Progression 0..100, bornée pour une barre de progression. `0` si le montant visé est invalide. */
    fun progressPercent(currentAmount: Long, targetAmount: Long): Int {
        if (targetAmount <= 0L) return 0
        return ((currentAmount.coerceAtLeast(0L) * 100) / targetAmount).coerceIn(0L, 100L).toInt()
    }

    fun isCompleted(currentAmount: Long, targetAmount: Long): Boolean =
        targetAmount > 0L && currentAmount >= targetAmount

    /** Reste à épargner, jamais négatif (un objectif dépassé n'a plus rien à épargner). */
    fun remainingAmount(currentAmount: Long, targetAmount: Long): Long =
        (targetAmount - currentAmount).coerceAtLeast(0L)

    fun deadlineOf(deadline: LocalDate?, isCompleted: Boolean, today: LocalDate): SavingsGoalDeadline {
        if (isCompleted) return SavingsGoalDeadline.Reached
        if (deadline == null) return SavingsGoalDeadline.None
        val days = ChronoUnit.DAYS.between(today, deadline).toInt()
        return when {
            days < 0 -> SavingsGoalDeadline.Overdue(deadline)
            days == 0 -> SavingsGoalDeadline.Today(deadline)
            else -> SavingsGoalDeadline.Remaining(deadline, days, isSoon = days <= DUE_SOON_DAYS)
        }
    }

    /**
     * Montant à épargner pour tenir l'échéance : le reste divisé par le nombre de MOIS COMPLETS
     * restants (un mois entamé ne compte pas, pour ne jamais sous-estimer l'effort), arrondi à
     * l'unité supérieure (jamais de centimes dans une suggestion).
     *
     * `null` : pas d'échéance, objectif atteint, ou échéance dépassée (plus rien à « tenir »).
     */
    fun suggestionOf(
        currentAmount: Long,
        targetAmount: Long,
        deadline: LocalDate?,
        today: LocalDate
    ): SavingsSuggestion? {
        if (deadline == null || deadline.isBefore(today)) return null
        val remaining = remainingAmount(currentAmount, targetAmount)
        if (remaining == 0L) return null

        val months = Period.between(today, deadline).toTotalMonths()
        return if (months < 1) {
            SavingsSuggestion.BeforeDeadline(roundUpToMajorUnit(remaining))
        } else {
            SavingsSuggestion.PerMonth(roundUpToMajorUnit(ceilDiv(remaining, months)))
        }
    }

    private fun ceilDiv(dividend: Long, divisor: Long): Long = (dividend + divisor - 1) / divisor

    private fun roundUpToMajorUnit(amountMinor: Long): Long =
        ceilDiv(amountMinor, Money.MINOR_UNITS_PER_MAJOR.toLong()) * Money.MINOR_UNITS_PER_MAJOR
}
