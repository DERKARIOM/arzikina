package com.arzikina.ne.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arzikina.ne.domain.repository.RecurringTransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Détection périodique en ARRIÈRE-PLAN des occurrences `PENDING` manquantes (voir cahier des
 * charges "Gestion automatique des transactions planifiées", section WorkManager) — complète
 * `MainActivity.generateMissingRecurringOccurrences`, qui ne s'exécute que lorsque l'app est
 * ouverte.
 *
 * Aucune UI ici, JAMAIS : ni notification, ni dialogue de validation — le dialogue
 * (`RecurringOccurrenceQueueDialogFragment`) reste réservé à l'app ouverte et interactive, voir la
 * doc de `MainActivity`. Ce Worker se limite à tenir les occurrences à jour en tâche de fond, pour
 * que la pastille du Dashboard et l'historique restent exacts même si l'utilisateur n'ouvre pas
 * l'app pendant plusieurs cycles — et pour préparer une future notification/widget (voir
 * instructions projet, "évolutivité"), qui pourront lire cette même donnée sans dépendre de
 * l'ouverture de l'app.
 *
 * `@HiltWorker`/`@AssistedInject` : seul moyen pour un `Worker` de recevoir une dépendance Hilt
 * (`RecurringTransactionRepository`) — `Context`/`WorkerParameters` restent fournis par
 * `WorkManager` lui-même via `@Assisted`, voir [com.arzikina.ne.ArzikinaApplication] pour le
 * câblage de `HiltWorkerFactory`.
 *
 * Appelle exactement la même méthode que `MainActivity` (`generateMissingOccurrences`), déjà
 * pensée pour un appel concurrent app + Worker (garde-fou `existsForDate`, voir sa doc) : aucun
 * risque de doublon si les deux se déclenchent au même moment.
 */
@HiltWorker
class RecurringOccurrencesWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val recurringTransactionRepository: RecurringTransactionRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        recurringTransactionRepository.generateMissingOccurrences()
        Result.success()
    } catch (cancellation: CancellationException) {
        // Ne JAMAIS intercepter une annulation de coroutine (ex. WorkManager qui arrête ce
        // Worker) : la laisser se propager est indispensable au bon fonctionnement de la
        // concurrence structurée — l'avaler ici romprait l'annulation sans avertir l'appelant.
        throw cancellation
    } catch (exception: Exception) {
        // Échec transitoire probable (ex. base momentanément verrouillée) : WorkManager
        // réessaiera avec un backoff exponentiel par défaut, jamais de perte silencieuse.
        Result.retry()
    }
}
