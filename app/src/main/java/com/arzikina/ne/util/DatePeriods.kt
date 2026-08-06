package com.arzikina.ne.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
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

    fun isInCurrentMonth(epochMillis: Long, today: LocalDate = LocalDate.now()): Boolean =
        YearMonth.from(toLocalDate(epochMillis)) == YearMonth.from(today)

    fun isInCurrentWeek(epochMillis: Long, today: LocalDate = LocalDate.now()): Boolean {
        val date = toLocalDate(epochMillis)
        val weekFields = WeekFields.ISO
        return date.get(weekFields.weekBasedYear()) == today.get(weekFields.weekBasedYear()) &&
            date.get(weekFields.weekOfWeekBasedYear()) == today.get(weekFields.weekOfWeekBasedYear())
    }
}
