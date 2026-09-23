package com.naniger.arzikina.presentation.components

import android.content.Context
import com.naniger.arzikina.R

/**
 * Message affiché à la fin d'une synchronisation manuelle (Paramètres et Dashboard). Extrait ici :
 * il était dupliqué à l'identique dans `SettingsFragment` et `DashboardFragment`.
 *
 * Chaque compteur passe par un `<plurals>` (« 1 envoyée » / « 3 envoyées », « 1 sent » / « 3 sent »)
 * au lieu de l'ancien « envoyée(s) », puis les trois morceaux sont assemblés par
 * `settings_sync_now_result`, dont l'ordre reste traduisible.
 */
fun Context.syncResultMessage(event: SyncButtonEvent): String = when (event) {
    is SyncButtonEvent.SyncError -> getString(R.string.settings_sync_now_error)
    is SyncButtonEvent.SyncFinished -> {
        val push = event.pushResult
        val pull = event.pullResult
        if (push.pushed == 0 && pull.received == 0) {
            getString(R.string.settings_sync_now_nothing_pending)
        } else {
            getString(
                R.string.settings_sync_now_result,
                resources.getQuantityString(R.plurals.sync_sent_count, push.succeeded, push.succeeded),
                resources.getQuantityString(R.plurals.sync_failed_count, push.failed, push.failed),
                resources.getQuantityString(R.plurals.sync_received_count, pull.applied, pull.applied)
            )
        }
    }
}
