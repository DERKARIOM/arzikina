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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reprogramme toutes les automatisations actives après [Intent.ACTION_BOOT_COMPLETED] (voir cahier
 * des charges "Ajouter l'heure de déclenchement à Automatisation", section 9 : "après un redémarrage
 * du téléphone, les automatisations doivent être reprogrammées automatiquement") : les alarmes
 * `AlarmManager` ne survivent JAMAIS à un redémarrage, contrairement au contenu de la base Room —
 * sans ce récepteur, plus aucune automatisation ne se déclencherait après le premier redémarrage
 * suivant sa création.
 *
 * [Intent.ACTION_MY_PACKAGE_REPLACED] écouté en plus (voir `AndroidManifest.xml`, Étape 7) : même
 * précaution que [Intent.ACTION_BOOT_COMPLETED], une mise à jour de l'application pouvant elle aussi,
 * selon les versions d'Android/constructeurs, invalider les alarmes déjà programmées — reprogrammer
 * dans les deux cas coûte la même vérification, aucune raison de s'en priver.
 *
 * Voir [AutomationAlarmReceiver] pour le raisonnement complet sur `@AndroidEntryPoint`/`goAsync()`.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var recurringTransactionRepository: RecurringTransactionRepository

    @Inject
    lateinit var automationScheduler: AutomationScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // `observeRecurringTransactions()` filtre déjà par utilisateur connecté (liste vide
                // si aucune session, voir son implémentation) — même source que partout ailleurs
                // dans l'app, aucune requête dédiée à écrire pour ce récepteur.
                val activeRules = recurringTransactionRepository.observeRecurringTransactions()
                    .first()
                    .filter { it.isActive }
                automationScheduler.rescheduleAll(activeRules)
            } catch (exception: Exception) {
                // Voir AutomationAlarmReceiver.onReceive pour le même raisonnement : mieux vaut
                // avaler une erreur inattendue ici que faire planter le processus au démarrage du
                // téléphone depuis un thread d'arrière-plan sans gestionnaire dédié.
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
