package com.arzikina.ne.presentation.utilities.recurring

import android.content.Context
import java.util.Calendar

/**
 * Formate une heure de déclenchement (`triggerHour`/`triggerMinute`, voir
 * [com.arzikina.ne.domain.model.RecurringTransaction]) en respectant le réglage 12h/24h de
 * l'appareil (voir [android.text.format.DateFormat.getTimeFormat], seule API qui le fait
 * automatiquement, contrairement à un `DateTimeFormatter` à motif fixe) — cahier des charges
 * "Ajouter l'heure de déclenchement à Automatisation", section 1.
 *
 * Partagé entre [RecurringTransactionFormFragment] (sélecteur + résumé du formulaire) et
 * [RecurringOccurrenceItemBinder] (sous-titre de chaque ligne "À traiter"/"À venir") pour ne
 * jamais dupliquer cette conversion `Calendar` → texte affichable.
 */
object TriggerTimeFormatter {

    fun format(context: Context, hour: Int, minute: Int): String {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        return android.text.format.DateFormat.getTimeFormat(context).format(calendar.time)
    }
}
