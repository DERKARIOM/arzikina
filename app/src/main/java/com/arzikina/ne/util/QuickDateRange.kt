package com.arzikina.ne.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * Raccourcis de sélection rapide pour la période fixe d'un budget (voir cahier des charges,
 * section quick-select : "Cette semaine"/"Ce mois"/"Mois prochain"/"Cette année"). Calculent des
 * DATES LITTÉRALES figées au moment de l'appel, à ne pas confondre avec [com.arzikina.ne.domain.model.BudgetPeriod]
 * (règle récurrente, recalculée en continu) : un budget "Ce mois" créé aujourd'hui garde ses
 * dates de départ/fin même une fois le mois suivant entamé, il ne "glisse" pas.
 *
 * "Personnalisée" n'a volontairement pas d'entrée ici : elle correspond à l'absence de sélection
 * rapide (l'utilisateur choisit ses dates via les sélecteurs `item_date_field.xml`/
 * `MaterialDatePicker`, voir `BudgetFormFragment`), pas à un calcul.
 */
enum class QuickDateRange {
    THIS_WEEK,
    THIS_MONTH,
    NEXT_MONTH,
    THIS_YEAR;

    /** Bornes inclusives (voir [Budget.startDate]/[Budget.endDate]). */
    fun toDateRange(today: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> = when (this) {
        THIS_WEEK -> {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            monday to sunday
        }
        THIS_MONTH -> {
            val month = YearMonth.from(today)
            month.atDay(1) to month.atEndOfMonth()
        }
        NEXT_MONTH -> {
            val month = YearMonth.from(today).plusMonths(1)
            month.atDay(1) to month.atEndOfMonth()
        }
        THIS_YEAR -> today.withDayOfYear(1) to today.withDayOfYear(today.lengthOfYear())
    }
}
