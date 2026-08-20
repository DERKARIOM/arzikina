package com.arzikina.ne.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.arzikina.ne.MainActivity
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.RecurringTransaction

/**
 * Notification de rappel posée par [AutomationAlarmReceiver] à l'heure exacte de déclenchement
 * d'une automatisation (voir cahier des charges "Ajouter l'heure de déclenchement à Automatisation",
 * section 5 : "Il est temps d'enregistrer votre transaction récurrente « Déjeuner »").
 *
 * `object` plutôt qu'injecté (même principe que [RecurringOccurrencesScheduler]) : aucun état propre
 * à conserver entre deux appels, seulement des fonctions prenant un [Context] en paramètre.
 *
 * Tap = ouvre simplement [MainActivity] (lancement standard, sans extra dédié) : si l'app était
 * fermée, son démarrage normal régénère déjà les occurrences dues et affiche le dialogue de
 * validation (voir `MainActivity.generateMissingRecurringOccurrences`), aucune duplication de
 * logique nécessaire. Si l'app était déjà ouverte en arrière-plan, le tap la ramène simplement au
 * premier plan (le dialogue ne se rouvre pas automatiquement dans ce cas précis) — un deep-link
 * direct vers le dialogue depuis une session déjà active interagirait avec le verrou biométrique de
 * réentrée (`MainActivity.checkBiometricReentryLock`) et mérite sa propre réflexion dédiée plutôt
 * qu'un ajout hâtif ici qui risquerait de le contourner.
 *
 * Permission `POST_NOTIFICATIONS` (Android 13+, voir `AndroidManifest.xml`) : sans elle déclarée ET
 * accordée, [NotificationManagerCompat.notify] ne montre simplement rien, sans lever d'exception
 * (comportement documenté par Android) — aucune vérification supplémentaire nécessaire ici.
 */
object AutomationNotifier {

    fun notifyTrigger(context: Context, rule: RecurringTransaction) {
        ensureChannel(context)

        val label = rule.description.ifBlank { context.getString(R.string.automation_notification_fallback_name) }
        val contentIntent = PendingIntent.getActivity(
            context,
            rule.id.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_time_24)
            .setContentTitle(label)
            .setContentText(context.getString(R.string.automation_notification_text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(rule.id.toInt(), notification)
    }

    /** Idempotent (voir doc officielle `createNotificationChannel`) : peut être rappelée à chaque
     * notification sans effet indésirable, plus simple qu'un appel unique au démarrage de l'app. */
    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.automation_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.automation_notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private const val CHANNEL_ID = "automation_triggers"
}
