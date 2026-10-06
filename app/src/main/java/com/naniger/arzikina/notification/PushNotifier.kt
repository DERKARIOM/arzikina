package com.naniger.arzikina.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.naniger.arzikina.MainActivity
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.PushMessage
import com.naniger.arzikina.domain.model.PushMessageType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Affiche une [PushMessage] déjà validée. Les textes viennent TOUJOURS des ressources de
 * l'application (FR/EN), jamais du serveur.
 *
 * Identité de notification déterministe : tag `push:<TYPE>` + identifiant dérivé de l'entité. Un
 * second envoi pour le même événement remplace le premier au lieu de s'empiler, et le tag évite
 * toute collision avec les rappels d'automatisation (qui n'en ont pas).
 *
 * Toucher la notification ouvre l'application ; l'ouverture directe de la bonne page (prêt…) arrive
 * à l'étape « liens de notification », en passant par le verrou biométrique.
 *
 * Sans permission `POST_NOTIFICATIONS` (Android 13+) ou canal désactivé par l'utilisateur,
 * [NotificationManagerCompat.notify] n'affiche rien, sans erreur : comportement voulu.
 */
class PushNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private data class Content(val channelId: String, @StringRes val title: Int, @StringRes val text: Int, val priority: Int)

    fun show(message: PushMessage) {
        val content = contentFor(message.type)
        val tag = TAG_PREFIX + message.type.wireName
        val notificationId = (message.entityServerId ?: message.type.wireName).hashCode()

        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, content.channelId)
            .setSmallIcon(R.drawable.ic_stat_arzikina)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.ic_notification_arzikina_large))
            .setContentTitle(context.getString(content.title))
            .setContentText(context.getString(content.text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(content.text)))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(content.priority)
            // Contenu masqué sur l'écran de verrouillage : une application financière ne doit rien
            // révéler sans déverrouillage, même si nos textes restent génériques.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setWhen(message.sentAtMillis.takeIf { it > 0 } ?: System.currentTimeMillis())
            .setShowWhen(true)
            .build()

        NotificationManagerCompat.from(context).notify(tag, notificationId, notification)
    }

    private fun contentFor(type: PushMessageType): Content = when (type) {
        PushMessageType.SYSTEM_MESSAGE -> Content(
            NotificationChannels.GENERAL,
            R.string.app_name,
            R.string.push_system_message_text,
            NotificationCompat.PRIORITY_LOW
        )
        PushMessageType.LOAN_DUE -> Content(
            NotificationChannels.LOANS,
            R.string.push_loan_due_title,
            R.string.push_loan_due_text,
            NotificationCompat.PRIORITY_DEFAULT
        )
        PushMessageType.SECURITY_ALERT -> Content(
            NotificationChannels.SECURITY,
            R.string.push_security_alert_title,
            R.string.push_security_alert_text,
            NotificationCompat.PRIORITY_HIGH
        )
    }

    private companion object {
        const val TAG_PREFIX = "push:"
    }
}
