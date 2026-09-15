package com.arzikina.ne.util

import java.time.LocalDate
import java.time.YearMonth

/**
 * Préréglages de période pour l'écran Statistiques ([com.arzikina.ne.presentation.statistics.StatisticsViewModel]) —
 * MIROIR EXACT de `StatsPeriodPreset`/`resolveStatsPeriodRange` côté Web
 * (`arzikina-web-sync/src/services/finance.ts`), pour que "7 derniers jours"/"Mois précédent"/etc.
 * désignent rigoureusement la même plage de dates sur les deux plateformes.
 *
 * Volontairement DISTINCT de [QuickDateRange] (raccourcis de période FUTURE pour un budget à dates
 * fixes : Cette semaine/Ce mois/Mois prochain/Cette année, sans "mois précédent" ni "7/30 derniers
 * jours") : ces deux enums répondent à des besoins différents (période à venir vs période passée
 * d'analyse), les fusionner aurait mélangé deux vocabulaires métier distincts.
 */
enum class StatsPeriodPreset {
    MONTH,
    PREV_MONTH,
    LAST_7_DAYS,
    LAST_30_DAYS,
    YEAR,
    CUSTOM;

    /**
     * Bornes INCLUSIVES [start, end] en jours calendaires locaux pour ce préréglage — `null` pour
     * [CUSTOM], qui n'a pas de résolution automatique (les deux dates sont choisies librement par
     * l'utilisateur, voir `StatisticsViewModel.onCustomRangeChanged`).
     */
    fun toDateRange(today: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate>? = when (this) {
        MONTH -> {
            val month = YearMonth.from(today)
            month.atDay(1) to month.atEndOfMonth()
        }
        PREV_MONTH -> {
            val month = YearMonth.from(today).minusMonths(1)
            month.atDay(1) to month.atEndOfMonth()
        }
        LAST_7_DAYS -> today.minusDays(6) to today
        LAST_30_DAYS -> today.minusDays(29) to today
        YEAR -> today.withDayOfYear(1) to today.withDayOfYear(today.lengthOfYear())
        CUSTOM -> null
    }
}
