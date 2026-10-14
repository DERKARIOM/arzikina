package com.naniger.arzikina.util.external

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Ouvre une URL HTTPS dans le navigateur (ou l'application qui la gère). Point unique pour ouvrir
 * un lien externe : `UpdateLinkOpener` et l'écran « Application web » s'y appuient.
 *
 * @return `false` si le lien n'est pas HTTPS ou si aucune application ne peut l'ouvrir.
 */
object WebLinkOpener {

    fun open(context: Context, uri: Uri): Boolean {
        if (uri.scheme != "https" && uri.scheme != "market") return false
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    fun open(context: Context, url: String): Boolean = open(context, Uri.parse(url))
}
