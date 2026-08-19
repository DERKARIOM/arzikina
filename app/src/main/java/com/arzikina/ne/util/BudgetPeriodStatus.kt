package com.arzikina.ne.util

import com.arzikina.ne.domain.model.Budget
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Statut AUTOMATIQUE (jamais saisi ni stocké, contrairement à `PlanStatus`) d'un budget à période
 * fixe : recalculé à chaque affichage en comparant la date du jour à
 * [Budget.startDate]/[Budget.endDate] (voir cahier des charges "Amélioration de la fonctionnalité
 * Budget — Gestion d'une période", section statuts).
 *
 * Ne s'applique qu'aux budgets à période fixe : un budget récurrent (legacy, `startDate`/`endDate`
 * nuls, voir [Budget]) n'a pas de statut au sens de cette énumération — [of] retourne alors `null`.
 */
enum class BudgetPeriodStatus {
    UPCOMING,
    ONGOING,
    COMPLETED;

    companion object {
        /** `null` si [startDate]/[endDate] sont nuls (budget récurrent, voir la doc de tête). */
        fun of(startDate: Long?, endDate: Long?, today: LocalDate = LocalDate.now()): BudgetPeriodStatus? {
            if (startDate == null || endDate == null) return null
            val start = DatePeriods.toLocalDate(startDate)
            val end = DatePeriods.toLocalDate(endDate)
            return when {
                today.isBefore(start) -> UPCOMING
                today.isAfter(end) -> COMPLETED
                else -> ONGOING
            }
        }
    }
}

/**
 * "Jours restants" avant la fin de la période (0 le dernier jour inclus, valeur négative une fois
 * [endDate] dépassée — l'appelant doit vérifier [BudgetPeriodStatus] avant d'afficher ce nombre,
 * un budget Terminé n'a pas vocation à afficher un compte à rebours).
 */
fun daysRemaining(endDate: Long, today: LocalDate = LocalDate.now()): Long =
    ChronoUnit.DAYS.between(today, DatePeriods.toLocalDate(endDate))
