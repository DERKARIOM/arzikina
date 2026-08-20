package com.arzikina.ne.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.arzikina.ne.domain.model.RecurringTransaction
import com.arzikina.ne.domain.model.nextTriggerInstant
import com.arzikina.ne.domain.repository.AutomationScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Implémentation [AlarmManager] d'[AutomationScheduler] (voir sa doc pour le contrat complet et le
 * raisonnement architectural).
 *
 * `setAndAllowWhileIdle` (PAS `setExactAndAllowWhileIdle`) : choix délibéré, voir échange avec
 * l'utilisateur avant implémentation — précision quasi-exacte (Android peut retarder de quelques
 * minutes en Mode Économie d'énergie strict/Doze), mais NE NÉCESSITE AUCUNE permission spéciale
 * (contrairement à `SCHEDULE_EXACT_ALARM`, qui exigerait une redirection manuelle vers les réglages
 * système sur Android 13+ et une justification Play Store pour une app de budget). `RTC_WAKEUP` :
 * raisonne en heure murale (`System.currentTimeMillis()`), pas en temps écoulé depuis le démarrage
 * (`ELAPSED_REALTIME`) — indispensable pour une heure de déclenchement définie par l'utilisateur
 * (ex. "12:30"), et réveille l'appareil si besoin (une automatisation doit se déclencher même
 * téléphone en veille, voir cahier des charges section 9).
 *
 * `PendingIntent.FLAG_IMMUTABLE` : obligatoire depuis Android 12 pour tout `PendingIntent` dont le
 * contenu n'a pas besoin d'être modifié par le récepteur système — c'est le cas ici,
 * [AutomationAlarmReceiver] lit uniquement l'id transmis via `intent.getLongExtra`, jamais rempli a
 * posteriori. `requestCode = recurringTransactionId.toInt()` : un identifiant Room ne dépasse jamais
 * `Int.MAX_VALUE` en pratique dans cette application — voir `AccountRepositoryImpl` et consorts pour
 * la même hypothèse implicite (id `Long` généré par SQLite `AUTOINCREMENT`, jamais aussi grand).
 */
class AutomationSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AutomationScheduler {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService<AlarmManager>()

    override fun schedule(rule: RecurringTransaction) {
        cancel(rule.id)
        if (!rule.isActive) return
        val pendingIntent = pendingIntentFor(rule.id, createIfAbsent = true) ?: return
        // Voir la doc de classe : un instant déjà passé (ex. redémarrage tardif) déclenche
        // l'alarme immédiatement, comportement standard d'AlarmManager, volontairement pas
        // de branche spéciale ici (voir aussi `RecurringTransactionTriggerTime.kt`).
        alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, rule.nextTriggerInstant(), pendingIntent)
    }

    override fun cancel(recurringTransactionId: Long) {
        val pendingIntent = pendingIntentFor(recurringTransactionId, createIfAbsent = false) ?: return
        alarmManager?.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    override fun rescheduleAll(activeRules: List<RecurringTransaction>) {
        activeRules.forEach { schedule(it) }
    }

    /**
     * `FLAG_NO_CREATE` quand [createIfAbsent] est faux (voir [cancel]) : retourne `null` plutôt que
     * de créer inutilement un `PendingIntent` uniquement pour l'annuler dans la foulée — évite de
     * laisser une entrée fantôme dans le registre interne du système si aucune alarme n'était
     * réellement programmée pour cet id.
     */
    private fun pendingIntentFor(recurringTransactionId: Long, createIfAbsent: Boolean): PendingIntent? {
        val intent = Intent(context, AutomationAlarmReceiver::class.java).apply {
            putExtra(AutomationAlarmReceiver.EXTRA_RECURRING_TRANSACTION_ID, recurringTransactionId)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        if (!createIfAbsent) flags = flags or PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, recurringTransactionId.toInt(), intent, flags)
    }
}
