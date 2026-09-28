package com.naniger.arzikina.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Date + heure d'un prêt/emprunt (`Loan.startDate`) — Kotlin pur, testé par `LoanDateTimeTest`.
 * Même règles côté Web (`lib/loan-datetime.ts`).
 *
 * Stockage INCHANGÉ : `startDate` a toujours été un instant en millisecondes (colonne MySQL
 * `start_date BIGINT`), jamais une simple date — l'heure choisie y est simplement conservée, sans
 * migration ni nouvelle colonne. Même convention que `Transaction.date` : l'heure saisie est
 * interprétée dans le fuseau LOCAL de l'appareil (jamais UTC), convertie en instant absolu pour la
 * synchronisation, puis réaffichée dans le fuseau local du lecteur.
 *
 * Données antérieures sans heure : le sélecteur de date enregistrait jusqu'ici minuit local pile
 * (00:00:00.000, voir `LoanFormFragment.showDatePicker`). [hasExplicitTime] traite cette valeur
 * technique comme « pas d'heure » : elle n'est jamais affichée comme « 00:00 », et aucune donnée
 * n'est réécrite. Seule conséquence : un prêt volontairement saisi à 00:00 s'affiche sans heure.
 */
object LoanDateTime {

    /** `false` pour minuit local pile (valeur technique d'une date saisie sans heure). */
    fun hasExplicitTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime() != LocalTime.MIDNIGHT

    /** Même jour que [epochMillis], à l'heure [hour]:[minute] (secondes à 0). Heure/minute hors
     * bornes (ex. 25:90) refusées par [LocalTime.of] — jamais une date silencieusement décalée. */
    fun withTime(epochMillis: Long, hour: Int, minute: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        toLocalDate(epochMillis, zone).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    /** Nouvelle date [date], en CONSERVANT l'heure déjà choisie dans [currentEpochMillis]. */
    fun withDate(currentEpochMillis: Long, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atTime(toLocalTime(currentEpochMillis, zone)).atZone(zone).toInstant().toEpochMilli()

    /** Maintenant, à la minute (secondes/millisecondes à 0) : valeur par défaut du formulaire. */
    fun nowToMinute(nowEpochMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long {
        val time = toLocalTime(nowEpochMillis, zone)
        return withTime(nowEpochMillis, time.hour, time.minute, zone)
    }

    fun toLocalDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun toLocalTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalTime =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()

    /** Comparaison par JOUR calendaire local — les règles métier existantes (échéance, premier
     * versement) raisonnent en jours ; l'heure ajoutée ne doit pas les faire basculer. */
    fun isBeforeDay(a: Long, b: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        toLocalDate(a, zone).isBefore(toLocalDate(b, zone))
}
