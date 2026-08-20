package com.arzikina.ne

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.arzikina.ne.domain.repository.AutomationScheduler
import com.arzikina.ne.domain.repository.RecurringTransactionRepository
import com.arzikina.ne.work.RecurringOccurrencesScheduler
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
 *
 * [rescheduleActiveAutomations] : même raisonnement, ajouté lors de la vérification finale du
 * cahier des charges "Ajouter l'heure de déclenchement à Automatisation" — sans cet appel, une
 * règle créée AVANT cette fonctionnalité (migration 19→20, heure de repli 08:00) ne serait jamais
 * réellement programmée via `AlarmManager` tant que l'utilisateur ne la modifie pas explicitement
 * OU ne redémarre pas son téléphone ([com.arzikina.ne.work.BootCompletedReceiver]), ce qui ne
 * couvre pas le cas courant "mise à jour de l'app puis simple réouverture" (section 17, scénario
 * de test explicite "automatisation historique"). `AutomationScheduler.schedule` étant idempotent
 * (annule puis reprogramme), répéter cet appel à chaque lancement du processus est sans risque de
 * doublon.
 *
 * [PDFBoxResourceLoader.init] : requis par PdfBox-Android AVANT tout usage de la bibliothèque (voir
 * `data/receipts/ReceiptTextExtractor`, "Extraction du montant d'un reçu") — charge ses ressources
 * de polices une seule fois pour tout le processus. Idempotent et peu coûteux (pas de lecture de
 * fichier utilisateur), placé ici par cohérence avec les autres initialisations globales de cette
 * classe plutôt que dans `ReceiptTextExtractor` lui-même (qui resterait sinon appelé plusieurs fois
 * sans bénéfice, une fois par instance créée par Hilt).
 */
@HiltAndroidApp
class ArzikinaApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var recurringTransactionRepository: RecurringTransactionRepository

    @Inject
    lateinit var automationScheduler: AutomationScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        RecurringOccurrencesScheduler.schedule(this)
        rescheduleActiveAutomations()
    }

    private fun rescheduleActiveAutomations() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // `observeRecurringTransactions()` renvoie une liste vide sans session active (voir son
            // implémentation) : ne fait donc rien tant que personne n'est connecté, aucune vérification
            // supplémentaire nécessaire ici — même principe que BootCompletedReceiver.
            val activeRules = recurringTransactionRepository.observeRecurringTransactions().first().filter { it.isActive }
            automationScheduler.rescheduleAll(activeRules)
        }
    }
}
