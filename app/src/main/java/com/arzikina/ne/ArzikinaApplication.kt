package com.arzikina.ne

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.arzikina.ne.work.RecurringOccurrencesScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Classe [Application] annotée pour Hilt : c'est le point d'ancrage du graphe
 * de dépendances racine de toute l'application (SingletonComponent).
 *
 * [Configuration.Provider] : nécessaire pour que `WorkManager` construise ses `Worker` via
 * [HiltWorkerFactory] plutôt que par réflexion sans dépendances — sinon
 * `RecurringOccurrencesWorker` (voir sa doc, `@AssistedInject`) ne pourrait jamais recevoir
 * `RecurringTransactionRepository`. Nécessite de désactiver l'initialisation automatique par
 * défaut de `WorkManager` dans `AndroidManifest.xml` (voir ce fichier) — sinon `WorkManager`
 * s'initialiserait deux fois (l'automatique, sans Hilt, PUIS la nôtre), ce qui lève une exception
 * au démarrage.
 *
 * [RecurringOccurrencesScheduler.schedule] appelé une seule fois ici (pas dans `MainActivity`) :
 * la planification en arrière-plan doit exister dès le démarrage du PROCESSUS, indépendamment de
 * l'ouverture ou non d'un écran — voir la doc de [RecurringOccurrencesScheduler].
 */
@HiltAndroidApp
class ArzikinaApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        RecurringOccurrencesScheduler.schedule(this)
    }
}
