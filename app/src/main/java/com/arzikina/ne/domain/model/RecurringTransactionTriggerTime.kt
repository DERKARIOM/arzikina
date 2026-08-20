package com.arzikina.ne.domain.model

import java.time.Instant
import java.time.ZoneId

/**
 * Combine un jour calendaire (minuit local — même convention que [RecurringTransaction.nextExecutionDate]/
 * [RecurringTransaction.startDate], voir leur doc) avec [RecurringTransaction.triggerHour]/
 * [RecurringTransaction.triggerMinute] pour obtenir l'instant EXACT à programmer (voir cahier des
 * charges "Ajouter l'heure de déclenchement à Automatisation").
 *
 * Fonction pure (aucun accès Room/AlarmManager), réutilisée à la fois par
 * [com.arzikina.ne.work.AutomationScheduler] (programmer l'alarme) et par l'affichage "Prochaine
 * exécution" (liste/Dashboard, sections 2/14 du cahier des charges) — même principe que
 * [computeNextExecutionDate] : un seul calcul, jamais dupliqué entre programmation et affichage.
 *
 * Si l'instant obtenu est déjà passé (ex. téléphone redémarré après l'heure programmée du jour même,
 * ou règle jamais programmée avant l'ajout de cette fonctionnalité), aucun traitement spécial ici :
 * `AlarmManager` déclenche immédiatement une alarme programmée dans le passé (comportement standard
 * documenté par Android) — même philosophie de rattrapage que [generateMissingScheduledDates] pour
 * les échéances manquées, sans dupliquer cette logique de "rattrapage" à cet endroit.
 */
fun RecurringTransaction.nextTriggerInstant(): Long =
    combineDayAndTime(nextExecutionDate, triggerHour, triggerMinute)

/** Voir [RecurringTransaction.nextTriggerInstant] : extraite pour être appelable directement avec un
 * jour arbitraire (ex. une échéance déjà générée dans `generateMissingScheduledDates`), sans avoir à
 * construire un [RecurringTransaction] complet. */
fun combineDayAndTime(dayEpochMillis: Long, hour: Int, minute: Int): Long {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(dayEpochMillis).atZone(zone).toLocalDate()
    return day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
}
