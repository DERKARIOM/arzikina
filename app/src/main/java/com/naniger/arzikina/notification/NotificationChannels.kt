package com.naniger.arzikina.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import com.naniger.arzikina.R

/**
 * Registre UNIQUE des canaux de notification d'Arzikina (Android 8+). Les identifiants sont
 * définitifs : Android conserve les réglages de l'utilisateur (son, importance, désactivation) par
 * identifiant, en changer un ferait perdre ces choix.
 *
 * Créés dès le démarrage ([ensureCreated], appelé par `ArzikinaApplication`) pour apparaître dans
 * les réglages système avant même la première notification. `createNotificationChannels` est
 * idempotent : il ne modifie jamais l'importance choisie par l'utilisateur, seulement le nom et la
 * description (mis à jour si la langue change).
 *
 * Pas de canal « synchronisation » : une synchronisation réussie n'a pas à notifier.
 */
object NotificationChannels {

    /** Rappels d'automatisation, posés localement (voir `work/AutomationNotifier`). Inchangé. */
    const val AUTOMATION = "automation_triggers"
    /** Échéances de prêts et d'emprunts (push serveur). */
    const val LOANS = "loans"
    /** Informations d'Arzikina (push serveur). */
    const val GENERAL = "general"
    /** Sécurité du compte (push serveur). */
    const val SECURITY = "security"

    private data class Spec(
        val id: String,
        @StringRes val name: Int,
        @StringRes val description: Int,
        val importance: Int
    )

    private val specs = listOf(
        Spec(
            AUTOMATION,
            R.string.automation_notification_channel_name,
            R.string.automation_notification_channel_description,
            NotificationManager.IMPORTANCE_DEFAULT
        ),
        Spec(LOANS, R.string.notification_channel_loans_name, R.string.notification_channel_loans_description, NotificationManager.IMPORTANCE_DEFAULT),
        Spec(GENERAL, R.string.notification_channel_general_name, R.string.notification_channel_general_description, NotificationManager.IMPORTANCE_LOW),
        Spec(SECURITY, R.string.notification_channel_security_name, R.string.notification_channel_security_description, NotificationManager.IMPORTANCE_HIGH)
    )

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannels(
            specs.map { spec ->
                NotificationChannel(spec.id, context.getString(spec.name), spec.importance).apply {
                    description = context.getString(spec.description)
                }
            }
        )
    }
}
