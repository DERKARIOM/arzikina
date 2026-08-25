package com.arzikina.ne.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arzikina.ne.domain.repository.SyncAuthRepository
import com.arzikina.ne.domain.repository.SyncEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Exécution en ARRIÈRE-PLAN du Sync Engine (voir [SyncEngine], docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md) —
 * même schéma que [RecurringOccurrencesWorker] (`@HiltWorker`/`@AssistedInject`, voir sa doc pour le
 * câblage `HiltWorkerFactory`). Déclenché par [SyncWorkScheduler] : périodiquement, ou juste après
 * un retour de connectivité (voir [SyncConnectivityObserver]).
 *
 * Même ordre que le déclenchement manuel (voir `SettingsViewModel.syncNow`) : push PUIS pull.
 *
 * [SyncAuthRepository.getActiveSession] vérifié EN PREMIER : sur la quasi-totalité des appareils,
 * personne n'est connecté au serveur de synchronisation (fonctionnalité optionnelle, voir l'écran
 * de connexion dédié) — inutile de dépenser réseau/batterie pour un `SyncEngine` qui n'aurait de
 * toute façon rien à faire. Un `Result.success()` immédiat ici, PAS `Result.retry()` : ce n'est pas
 * un échec, juste rien à synchroniser pour cet appareil.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncAuthRepository: SyncAuthRepository,
    private val syncEngine: SyncEngine
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        if (syncAuthRepository.getActiveSession() == null) {
            Result.success()
        } else {
            syncEngine.pushPendingChanges()
            syncEngine.pullRemoteChanges()
            Result.success()
        }
    } catch (cancellation: CancellationException) {
        // Ne JAMAIS intercepter une annulation de coroutine (ex. WorkManager qui arrête ce
        // Worker, contrainte réseau perdue en cours d'exécution) — voir RecurringOccurrencesWorker.
        throw cancellation
    } catch (exception: Exception) {
        // Échec réseau/serveur transitoire probable : WorkManager réessaiera avec un backoff
        // exponentiel par défaut, jamais de perte silencieuse (les entrées `sync_queue` concernées
        // restent de toute façon PENDING/FAILED, voir SyncEngineImpl — rien n'est perdu même sans ce retry).
        Result.retry()
    }
}
