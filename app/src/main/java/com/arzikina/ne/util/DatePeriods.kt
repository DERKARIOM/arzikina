package com.arzikina.ne.util

import com.arzikina.ne.domain.model.BudgetPeriod
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

/**
 * Correspondance "date dans la période en cours", partagée entre les Budgets
 * (dépassement hebdomadaire/mensuel) et le filtre par période de la
 * recherche de Transactions — centralisée ici pour éviter de dupliquer cette
 * logique à chaque nouvel écran qui raisonne en semaine/mois civils.
 */
object DatePeriods {

    fun toLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

    /** Voir [toLocalDate] : même conversion, pour l'heure (ex. "12:30" sur une ligne de transaction). */
    fun toLocalTime(epochMillis: Long): LocalTime =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime()

    fun isInCurrentMonth(epochMillis: Long, today: LocalDate = LocalDate.now()): Boolean =
        YearMonth.from(toLocalDate(epochMillis)) == YearMonth.from(today)

    fun isInCurrentWeek(epochMillis: Long, today: LocalDate = LocalDate.now()): Boolean {
        val date = toLocalDate(epochMillis)
        val weekFields = WeekFields.ISO
        return date.get(weekFields.weekBasedYear()) == today.get(weekFields.weekBasedYear()) &&
            date.get(weekFields.weekOfWeekBasedYear()) == today.get(weekFields.weekOfWeekBasedYear())
    }

    /**
     * Dernier jour de la période en cours pour [period] (ex. utilisé pour
     * afficher "Expire le ..." sur un budget, voir [com.arzikina.ne.presentation.budget.BudgetAdapter]) :
     * fin du mois civil pour [BudgetPeriod.MONTHLY], dimanche de la semaine
     * ISO en cours pour [BudgetPeriod.WEEKLY].
     */
    fun currentPeriodEnd(period: BudgetPeriod, today: LocalDate = LocalDate.now()): LocalDate =
        when (period) {
            BudgetPeriod.MONTHLY -> YearMonth.from(today).atEndOfMonth()
            BudgetPeriod.WEEKLY -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        }
}
