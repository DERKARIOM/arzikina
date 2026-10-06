package com.naniger.arzikina.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.naniger.arzikina.domain.model.PushRegistrationOutcome
import com.naniger.arzikina.domain.repository.PushRegistrationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Enregistre l'appareil pour les notifications push (voir [PushRegistrationRepository.registerNow]).
 * Seul [PushRegistrationOutcome.Retry] provoque un nouvel essai, avec un backoff exponentiel et un
 * nombre d'essais plafonné : chaque démarrage de l'application relance de toute façon une demande.
 */
@HiltWorker
class PushRegistrationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pushRegistrationRepository: PushRegistrationRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        when (pushRegistrationRepository.registerNow()) {
            PushRegistrationOutcome.Retry ->
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
            PushRegistrationOutcome.Registered,
            PushRegistrationOutcome.NotSignedIn,
            PushRegistrationOutcome.Rejected -> Result.success()
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (exception: Exception) {
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    private companion object {
        const val MAX_ATTEMPTS = 8
    }
}

/**
 * Planification de [PushRegistrationWorker]. `REPLACE` : seule la demande la plus récente compte
 * (le travail relit l'état courant au moment de s'exécuter, l'enregistrement est idempotent).
 */
object PushRegistrationScheduler {

    private const val UNIQUE_WORK_NAME = "push_registration"
    private const val INITIAL_BACKOFF_SECONDS = 30L

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<PushRegistrationWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
