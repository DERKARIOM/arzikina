package com.arzikina.ne.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arzikina.ne.domain.repository.ProfilePhotoRepository
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
 * Même ordre que le déclenchement manuel (voir `SettingsViewModel.syncNow`) : push PUIS pull, PUIS
 * la photo de profil (voir [ProfilePhotoRepository.syncWithServer]) — volontairement APRÈS le
 * registre générique, mais dans le MÊME Worker/la même planification plutôt qu'un Worker dédié :
 * la photo a exactement les mêmes besoins (réseau requis, retry avec backoff, déclenchement
 * périodique + sur retour de connectivité) que le reste du Sync Engine, dupliquer cette
 * infrastructure n'aurait aucun bénéfice (cahier des charges "Gestion de la photo de profil",
 * "ne casse surtout pas le système actuel de synchronisation... extension propre de l'architecture
 * existante").
 *
 * [SyncAuthRepository.getActiveSession] vérifié EN PREMIER : sur la quasi-totalité des appareils,
 * personne n'est connecté au serveur de synchronisation (fonctionnalité optionnelle, voir l'écran
 * de connexion dédié) — inutile de dépenser réseau/batterie pour un `SyncEngine`/[ProfilePhotoRepository]
 * qui n'auraient de toute façon rien à faire. Un `Result.success()` immédiat ici, PAS
 * `Result.retry()` : ce n'est pas un échec, juste rien à synchroniser pour cet appareil.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncAuthRepository: SyncAuthRepository,
    private val syncEngine: SyncEngine,
    private val profilePhotoRepository: ProfilePhotoRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        if (syncAuthRepository.getActiveSession() == null) {
            Result.success()
        } else {
            // Chaque étape loggée SÉPARÉMENT (voir TAG ci-dessous) : un simple "Result.retry()"
            // silencieux rendait impossible de savoir laquelle des trois échouait réellement (bug
            // remonté par l'utilisateur — "Worker result RETRY" en boucle dans logcat, sans aucune
            // trace de la cause). Chaque bloc logue AVANT de relancer l'exception, pour que le
            // catch générique ci-dessous continue de piloter Result.retry() sans dupliquer cette
            // logique.
            runCatching { syncEngine.pushPendingChanges() }
                .onFailure { Log.e(TAG, "Échec pushPendingChanges()", it) }
                .getOrThrow()
            runCatching { syncEngine.pullRemoteChanges() }
                .onFailure { Log.e(TAG, "Échec pullRemoteChanges()", it) }
                .getOrThrow()
            runCatching { profilePhotoRepository.syncWithServer() }
                .onFailure { Log.e(TAG, "Échec profilePhotoRepository.syncWithServer()", it) }
                .getOrThrow()
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
        // L'exception exacte est déjà loggée ci-dessus (avec le nom de l'étape qui a échoué) : voir
        // logcat, tag "SyncWorker".
        Result.retry()
    }
}

private const val TAG = "SyncWorker"
