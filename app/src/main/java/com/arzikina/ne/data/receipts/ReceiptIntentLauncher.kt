package com.arzikina.ne.data.receipts

import android.content.Context
import android.content.Intent
import com.arzikina.ne.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Point d'accès UNIQUE aux `Intent`s sortants d'un reçu (Partager/Ouvrir avec une autre application)
 * — cahier des charges "Gestion des reçus", sections 6/8/21. Même statut que
 * [com.arzikina.ne.util.external.ExternalAppLauncher] : DÉLIBÉRÉMENT hors du domaine (types Android
 * irréductibles), injecté directement dans `ReceiptDetailViewModel`, `@ApplicationContext` +
 * `FLAG_ACTIVITY_NEW_TASK` pour la même raison (aucune méthode ici n'a besoin d'une `Activity`
 * précise).
 *
 * Utilise TOUJOURS [ReceiptFileStorage.contentUriFor] (jamais un chemin de fichier privé exposé
 * directement, voir sa doc) — cahier des charges section 8/9 : "ne jamais exposer un chemin de
 * fichier privé", "URI content:// sécurisée uniquement".
 */
@Singleton
class ReceiptIntentLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val receiptFileStorage: ReceiptFileStorage
) {

    /** `false` sans jamais planter (aucune application de partage disponible, permission révoquée
     * entre-temps...) — même convention que [com.arzikina.ne.util.external.ExternalAppLauncher.launch]. */
    fun share(relativePath: String, mimeType: String): Boolean {
        val uri = receiptFileStorage.contentUriFor(relativePath)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, context.getString(R.string.receipt_detail_share_chooser_title))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(chooser) }.isSuccess
    }

    /** `false` si aucune application installée ne sait ouvrir ce type de fichier (ou toute autre
     * erreur système) — jamais d'exception propagée. */
    fun openWithAnotherApp(relativePath: String, mimeType: String): Boolean {
        val uri = receiptFileStorage.contentUriFor(relativePath)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(viewIntent, context.getString(R.string.receipt_detail_open_with_chooser_title))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(chooser) }.isSuccess
    }
}
