package com.naniger.arzikina.presentation.utilities.issuedreceipts.history

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Conversions de dates du filtre de l'historique, pures (fuseau injecté) donc testables.
 * Les dates d'émission sont stockées au DÉBUT du jour local ; les bornes de filtre sont inclusives.
 */
object HistoryDateBounds {

    /** Jour choisi dans un `MaterialDatePicker` (minuit UTC) → début de ce jour en heure locale. */
    fun startOfDayFromPicker(pickerUtcMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(pickerUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()

    /** Dernière milliseconde du jour local de [localMillis]. */
    fun endOfDay(localMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(localMillis).atZone(zone).toLocalDate()
            .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    fun startOfDay(localMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(localMillis).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()

    /** Date locale [localMillis] → sélection initiale d'un `MaterialDatePicker` (minuit UTC du même jour). */
    fun toPickerSelection(localMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(localMillis).atZone(zone).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
