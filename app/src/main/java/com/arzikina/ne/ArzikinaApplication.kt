package com.arzikina.ne

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.arzikina.ne.domain.model.ThemeMode
import com.arzikina.ne.domain.repository.AutomationScheduler
import com.arzikina.ne.domain.repository.RecurringTransactionRepository
import com.arzikina.ne.domain.repository.UserPreferencesRepository
import com.arzikina.ne.work.RecurringOccurrencesScheduler
import com.arzikina.ne.work.SyncConnectivityObserver
import com.arzikina.ne.work.SyncWorkScheduler
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
 *
 * [applyStoredThemeMode] : appelé en tout premier, avant toute autre initialisation — voir sa doc.
 * Déplacé depuis `MainActivity.onCreate()` (chantier "thème sombre par défaut") : y était appelé
 * APRÈS `super.onCreate()`, donc après qu'`AppCompatActivity` ait déjà résolu ses ressources
 * night-mode selon le mode par défaut alors en vigueur (`MODE_NIGHT_FOLLOW_SYSTEM` tant que rien
 * n'a encore été fixé ce process) — si la préférence stockée en différait, `setDefaultNightMode`
 * déclenchait un `recreate()` de l'Activity déjà affichée, donc un flash visible au démarrage. Ici,
 * `AppCompatDelegate.setDefaultNightMode` est fixé AVANT la création de la moindre `Activity` :
 * plus aucun `recreate()` possible, `MainActivity` s'inflate directement dans le bon mode.
 */
@HiltAndroidApp
class ArzikinaApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var recurringTransactionRepository: RecurringTransactionRepository

    @Inject
    lateinit var automationScheduler: AutomationScheduler

    @Inject
    lateinit var syncConnectivityObserver: SyncConnectivityObserver

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        applyStoredThemeMode()
        PDFBoxResourceLoader.init(applicationContext)
        RecurringOccurrencesScheduler.schedule(this)
        rescheduleActiveAutomations()
        SyncWorkScheduler.schedulePeriodic(this)
        syncConnectivityObserver.start()
    }

    /**
     * Applique la préférence de thème (Système/Clair/Sombre, voir Paramètres) AVANT la création de
     * la première `Activity` — voir la KDoc de tête de la classe pour le raisonnement complet
     * (élimine le flash/`recreate()` qu'aurait provoqué un appel plus tardif dans
     * `MainActivity.onCreate`). Lecture bloquante ponctuelle de DataStore/Room (source locale, quasi
     * instantanée, même principe que l'ancienne `MainActivity.applyStoredThemeMode`) : acceptable ici
     * car [Application.onCreate] s'exécute déjà sur le thread principal avant tout affichage, aucune
     * UI n'est retardée au-delà de ce qui l'aurait été de toute façon.
     *
     * Défaut [ThemeMode.DARK] tant qu'aucune préférence n'a jamais été enregistrée (voir
     * `UserPreferences.themeMode`) — un choix explicite (Clair, Système ou Sombre) reste toujours
     * respecté, cette fonction ne fait que refléter fidèlement [UserPreferencesRepository.observePreferences].
     *
     * Limitation PRÉEXISTANTE, inchangée par ce déplacement (hors périmètre du chantier "thème
     * sombre par défaut") : `SettingsViewModel.onThemeModeChange` persiste le choix immédiatement,
     * mais n'appelle ni `AppCompatDelegate.setDefaultNightMode` ni `recreate()` — le nouveau mode ne
     * s'applique visuellement qu'au prochain démarrage du processus (via CETTE fonction), pas tant
     * que l'app reste ouverte après le choix dans Paramètres.
     */
    private fun applyStoredThemeMode() {
        val preferences = runBlocking { userPreferencesRepository.observePreferences().first() }
        val nightMode = when (preferences.themeMode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
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
