package com.naniger.arzikina.presentation.components

import android.content.Context
import com.naniger.arzikina.R
import com.naniger.arzikina.util.AppDateFormats
import com.naniger.arzikina.util.TriggerTimeFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Valeur de la ligne « Date et heure » des formulaires (`item_date_field.xml`) :
 * « Aujourd'hui - 08/08/2026 · 15:41 », « 05/08/2026 · 09:30 ». Le libellé relatif réutilise les
 * chaînes des en-têtes de jour des listes de transactions ([R.string.transaction_day_today]/
 * [R.string.transaction_day_yesterday]) ; l'heure suit le réglage 12 h / 24 h de l'appareil (voir
 * [TriggerTimeFormatter]).
 *
 * Partagé par le formulaire de transaction et « Enregistrer comme transaction » (dépense prévue),
 * pour qu'une même date s'affiche partout de la même façon.
 */
object DateTimeRowFormatter {

    fun format(context: Context, dateTimeMillis: Long, today: LocalDate = LocalDate.now()): String {
        val zonedDateTime = Instant.ofEpochMilli(dateTimeMillis).atZone(ZoneId.systemDefault())
        val date = zonedDateTime.toLocalDate()
        val relativeLabel = when (date) {
            today -> context.getString(R.string.transaction_day_today)
            today.minusDays(1) -> context.getString(R.string.transaction_day_yesterday)
            else -> null
        }
        val datePart = date.format(AppDateFormats.NUMERIC_DATE)
        val timePart = TriggerTimeFormatter.format(context, zonedDateTime.hour, zonedDateTime.minute)
        val dateLabel = if (relativeLabel != null) "$relativeLabel - $datePart" else datePart
        return "$dateLabel · $timePart"
    }
}
