package com.arzikina.ne.util

import com.arzikina.ne.domain.model.Budget
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * État pédagogique du curseur "Aujourd'hui" — mêmes 3 valeurs et même seuil de tolérance que la
 * version Web (`BudgetPaceState`/`budgetPaceStatus()` dans `services/finance.ts`), gardés
 * STRICTEMENT identiques entre les deux plateformes (voir prompt "Barre de progression
 * intelligente du budget avec curseur 'Aujourd'hui'").
 */
enum class BudgetPaceState { ON_TRACK, AHEAD, OVER }

/**
 * Position pédagogique du curseur "Aujourd'hui" sur la barre de progression d'un budget : compare
 * le rythme RÉEL de dépense au rythme THÉORIQUE (montant limite / durée totale × jours écoulés),
 * avec une tolérance de ±5 % par défaut pour absorber le bruit normal (un gros achat groupé ne
 * doit pas à lui seul déclencher "dépassement").
 *
 * [periodStatus] est TOUJOURS renseigné ici (contrairement à [BudgetPeriodStatus.of], qui retourne
 * `null` pour un budget récurrent) : un budget récurrent est par construction toujours "en cours"
 * sur sa période civile courante, ce qui reste une information utile pour l'affichage.
 */
data class BudgetPace(
    val periodStatus: BudgetPeriodStatus,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    /** Durée totale de la période, en jours (bornes incluses : 1er → 30 = 30 jours). */
    val totalDays: Long,
    val elapsedDays: Long,
    val daysRemaining: Long,
    /** 0f..1f — position du curseur "Aujourd'hui" sur la barre. */
    val elapsedRatio: Float,
    val dailyTheoretical: Double,
    val theoreticalSpentToDate: Double,
    /** dépensé cumulé - budget théorique du jour : positif = au-dessus du rythme, négatif = en avance. */
    val gap: Double,
    val paceState: BudgetPaceState,
) {
    companion object {
        /**
         * Résout les bornes RÉELLES (jour calendaire, début/fin) de la période d'un budget — même
         * répartition fixe/récurrent que [BudgetProgress.isInBudgetPeriod] (privée, non réutilisable
         * telle quelle), mais ici on a besoin des DEUX bornes, pas seulement d'un test
         * d'appartenance.
         */
        private fun resolveBounds(budget: Budget, today: LocalDate): Pair<LocalDate, LocalDate> {
            val startDate = budget.startDate
            val endDate = budget.endDate
            return if (startDate != null && endDate != null) {
                DatePeriods.toLocalDate(startDate) to DatePeriods.toLocalDate(endDate)
            } else {
                DatePeriods.currentPeriodStart(budget.period, today) to
                    DatePeriods.currentPeriodEnd(budget.period, today)
            }
        }

        /**
         * Cas limites gérés explicitement, mêmes règles que côté Web : avant le début (0 jour
         * écoulé, [BudgetPeriodStatus.UPCOMING]), après la fin (période entière écoulée,
         * [BudgetPeriodStatus.COMPLETED]), montant limite ou durée nulle (repli sans division par
         * zéro grâce à `totalDays.coerceAtLeast(1)`).
         */
        fun of(
            budget: Budget,
            spentMinor: Long,
            today: LocalDate = LocalDate.now(),
            toleranceRatio: Double = 0.05,
        ): BudgetPace {
            val (start, end) = resolveBounds(budget, today)
            val totalDays = (ChronoUnit.DAYS.between(start, end) + 1).coerceAtLeast(1)
            val periodStatus = when {
                today.isBefore(start) -> BudgetPeriodStatus.UPCOMING
                today.isAfter(end) -> BudgetPeriodStatus.COMPLETED
                else -> BudgetPeriodStatus.ONGOING
            }
            val clampedToday = when {
                today.isBefore(start) -> start
                today.isAfter(end) -> end
                else -> today
            }
            val elapsedDays = if (periodStatus == BudgetPeriodStatus.UPCOMING) {
                0L
            } else {
                ChronoUnit.DAYS.between(start, clampedToday) + 1
            }
            val elapsedRatio = (elapsedDays.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)
            val limit = budget.limitAmount.toDouble()
            val dailyTheoretical = limit / totalDays.toDouble()
            val theoreticalSpentToDate = dailyTheoretical * elapsedDays
            val gap = spentMinor.toDouble() - theoreticalSpentToDate
            val toleranceAmount = limit * toleranceRatio
            val paceState = when {
                gap > toleranceAmount -> BudgetPaceState.OVER
                gap < -toleranceAmount -> BudgetPaceState.AHEAD
                else -> BudgetPaceState.ON_TRACK
            }
            return BudgetPace(
                periodStatus = periodStatus,
                periodStart = start,
                periodEnd = end,
                totalDays = totalDays,
                elapsedDays = elapsedDays,
                daysRemaining = (totalDays - elapsedDays).coerceAtLeast(0),
                elapsedRatio = elapsedRatio,
                dailyTheoretical = dailyTheoretical,
                theoreticalSpentToDate = theoreticalSpentToDate,
                gap = gap,
                paceState = paceState,
            )
        }
    }
}
