package com.arzikina.ne.util

import android.content.Context
import java.util.Calendar

/**
 * Formate une heure (`hour`/`minute`, ex. `RecurringTransaction.triggerHour`/`triggerMinute`, ou
 * l'heure d'un [com.arzikina.ne.domain.model.Receipt.receivedAt]) en respectant le réglage 12h/24h
 * de l'appareil (voir [android.text.format.DateFormat.getTimeFormat], seule API qui le fait
 * automatiquement, contrairement à un `DateTimeFormatter` à motif fixe).
 *
 * Déplacé dans `util/` (initialement propre à `presentation/utilities/recurring/`, voir cahier des
 * charges "Ajouter l'heure de déclenchement à Automatisation") lors de l'ajout de "Gestion des
 * reçus" (voir `presentation/utilities/receipts/ReceiptsAdapter`) pour être partagé entre les deux
 * fonctionnalités, plutôt que de dupliquer cette conversion `Calendar` → texte affichable — même
 * principe que [DayLabel].
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
