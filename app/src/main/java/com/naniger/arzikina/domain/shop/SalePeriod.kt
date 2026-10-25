package com.naniger.arzikina.domain.shop

import java.time.Instant
import java.time.ZoneId

/**
 * Période prédéfinie de l'historique. Les bornes sont calculées en jours CALENDAIRES du fuseau de
 * l'utilisateur (« aujourd'hui » commence à minuit local), jamais en multiples de 24 h : un changement
 * d'heure ne décale donc pas la période.
 */
enum class SalePeriod {
    ALL, TODAY, LAST_7_DAYS, LAST_30_DAYS;

    /** `[from, to)` en millisecondes ; `to` est le début de demain. */
    fun bounds(nowMillis: Long, zone: ZoneId): Pair<Long, Long> {
        if (this == ALL) return Long.MIN_VALUE to Long.MAX_VALUE
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val days = when (this) {
            TODAY -> 0L
            LAST_7_DAYS -> 6L
            LAST_30_DAYS -> 29L
            ALL -> 0L
        }
        val from = today.minusDays(days).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return from to to
    }
}
