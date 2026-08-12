package com.arzikina.ne.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Planifie [RecurringOccurrencesWorker] — appelé une fois depuis
 * [com.arzikina.ne.ArzikinaApplication.onCreate], jamais depuis un Fragment/ViewModel (même
 * principe qu'[com.arzikina.ne.util.SystemBars], un utilitaire sans état plutôt qu'un objet à
 * injecter).
 */
object RecurringOccurrencesScheduler {

    /** Toutes les [INTERVAL_HOURS] : les occurrences se raisonnent en jours calendaires, pas en
     * minutes (voir `generateMissingScheduledDates`) — inutile de vérifier plus souvent, au prix
     * de la batterie, pour un résultat qui ne changerait pas plus vite. `KEEP` (pas `REPLACE`) :
     * n'annule/ne reprogramme JAMAIS une tâche déjà planifiée à chaque démarrage de l'app — une
     * seule planification suffit pour toute la durée de vie de l'installation. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<RecurringOccurrencesWorker>(INTERVAL_HOURS, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private const val UNIQUE_WORK_NAME = "recurring_occurrences_detection"
    private const val INTERVAL_HOURS = 12L
}
