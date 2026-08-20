package com.arzikina.ne.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arzikina.ne.domain.repository.AutomationScheduler
import com.arzikina.ne.domain.repository.RecurringTransactionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reçoit l'alarme programmée par [AutomationSchedulerImpl] à l'heure de déclenchement exacte d'une
 * automatisation (voir cahier des charges "Ajouter l'heure de déclenchement à Automatisation",
 * sections 4/5).
 *
 * `@AndroidEntryPoint` : seul moyen pour un [BroadcastReceiver] de recevoir des dépendances Hilt par
 * injection de champs (contrairement à [RecurringOccurrencesWorker], qui utilise `@AssistedInject`
 * — mécanisme réservé aux `Worker`, non disponible ici).
 *
 * `goAsync()` : indispensable pour tout travail asynchrone dans [onReceive] — un `BroadcastReceiver`
 * est normalement considéré "terminé" (et son processus éligible à être tué par le système) dès le
 * retour de [onReceive], bien avant qu'une coroutine lancée depuis lui n'ait eu le temps de
 * s'exécuter. `pendingResult.finish()` signale explicitement au système la fin réelle du travail.
 */
@AndroidEntryPoint
class AutomationAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var recurringTransactionRepository: RecurringTransactionRepository

    @Inject
    lateinit var automationScheduler: AutomationScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val recurringTransactionId = intent.getLongExtra(EXTRA_RECURRING_TRANSACTION_ID, NO_ID)
        if (recurringTransactionId == NO_ID) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handleTrigger(context, recurringTransactionId)
            } catch (exception: Exception) {
                // Pas de mécanisme de re-tentative ici (contrairement à `RecurringOccurrencesWorker`,
                // géré par WorkManager) : une automatisation manquée sera de toute façon rattrapée au
                // prochain appel de `generateMissingOccurrences` (ouverture de l'app ou Worker
                // périodique existant) — mieux vaut avaler l'erreur que faire planter le processus
                // depuis un thread d'arrière-plan sans gestionnaire d'exception non interceptée.
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Réutilise `generateMissingOccurrences()` (voir cahier des charges section 4 : "déclencher le
     * mécanisme EXISTANT de l'automatisation", pas une génération ciblée dupliquée) : la règle dont
     * l'alarme vient de se déclencher est justement due, elle sera donc traitée par ce même balayage
     * — comme toute autre règle qui serait due au même moment (déjà protégé contre les doublons via
     * `existsForDate`, voir son implémentation).
     */
    private suspend fun handleTrigger(context: Context, recurringTransactionId: Long) {
        recurringTransactionRepository.generateMissingOccurrences()

        // Rechargée APRÈS génération : `nextExecutionDate`/`isActive` ont pu changer (voir
        // `generateMissingOccurrences`), c'est cette version à jour qu'il faut reprogrammer.
        val rule = recurringTransactionRepository.getRecurringTransaction(recurringTransactionId) ?: return

        // `AlarmManager.setAndAllowWhileIdle` est un coup UNIQUE, jamais répété automatiquement par
        // le système : sans cet appel, une automatisation ne se déclencherait plus qu'une seule fois
        // dans toute son existence (voir AutomationScheduler.schedule, qui annule d'abord toute
        // alarme consommée avant d'en reposer une nouvelle si `isActive`).
        automationScheduler.schedule(rule)

        // Garde-fou ajouté lors de la vérification finale (cahier des charges section 16 : éviter
        // toute notification après désactivation) : une alarme déjà armée AVANT qu'une autre
        // exécution de `generateMissingOccurrences` (lancement d'app, Worker périodique) ne
        // désactive cette règle entre-temps — cas rare, voir la doc de
        // `RecurringTransactionRepositoryImpl.deactivateIfPastEndDate` (endDate raccourcie après
        // coup, sous la `nextExecutionDate` déjà avancée) — se déclencherait quand même une
        // dernière fois sans ce test, affichant à tort un rappel pour une automatisation déjà
        // terminée alors qu'aucune occurrence n'a été générée pour elle dans ce passage.
        if (rule.isActive) {
            AutomationNotifier.notifyTrigger(context, rule)
        }
    }

    companion object {
        const val EXTRA_RECURRING_TRANSACTION_ID = "recurring_transaction_id"
        private const val NO_ID = 0L
    }
}
