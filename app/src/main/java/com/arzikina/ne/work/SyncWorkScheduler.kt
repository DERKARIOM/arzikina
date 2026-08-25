package com.arzikina.ne.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Planifie [SyncWorker] — même principe que [RecurringOccurrencesScheduler] (utilitaire sans état,
 * jamais injecté). Deux points d'entrée distincts, voir chacun :
 * - [schedulePeriodic] : filet de sécurité en tâche de fond, indépendant de toute action utilisateur.
 * - [triggerNow] : réactif, appelé par [SyncConnectivityObserver] dès qu'une connexion réseau
 *   redevient disponible — la synchronisation n'attend pas le prochain cycle périodique.
 *
 * Contrainte `NetworkType.CONNECTED` sur les DEUX : `SyncWorker` échouerait de toute façon sans
 * réseau (voir `SyncApi`), autant laisser `WorkManager` différer l'exécution lui-même plutôt que de
 * consommer un cycle pour un échec certain.
 */
object SyncWorkScheduler {

    private val syncConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** `KEEP` (pas `REPLACE`) : une seule planification suffit pour toute la durée de vie de
     *  l'installation, jamais annulée/reprogrammée à chaque démarrage — même raisonnement que
     *  [RecurringOccurrencesScheduler.schedule]. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(syncConstraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** `REPLACE` (pas `KEEP`) : contrairement à la planification périodique, plusieurs appels
     *  rapprochés sont attendus ici (une connexion qui clignote peut déclencher [triggerNow]
     *  plusieurs fois de suite, voir [SyncConnectivityObserver]) — ne conserver que le plus récent
     *  évite d'empiler des exécutions redondantes.
     *
     *  `setExpedited` avec repli `RUN_AS_NON_EXPEDITED_WORK_REQUEST` : tente une exécution quasi
     *  immédiate (voir la documentation WorkManager sur le travail expédié), mais retombe
     *  silencieusement sur une file d'attente standard si le quota système du jour est épuisé —
     *  jamais d'échec pour cette seule raison. */
    fun triggerNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(syncConstraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private const val PERIODIC_UNIQUE_WORK_NAME = "sync_engine_periodic"
    private const val ONE_TIME_UNIQUE_WORK_NAME = "sync_engine_connectivity"
    private const val INTERVAL_HOURS = 6L
}
