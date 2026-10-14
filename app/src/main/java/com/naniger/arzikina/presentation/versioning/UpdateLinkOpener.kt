package com.naniger.arzikina.presentation.versioning

import android.content.Context
import android.net.Uri
import com.naniger.arzikina.util.external.WebLinkOpener

/**
 * Ouvre le lien de mise à jour configuré par le Super Admin. Un lien de fiche Google Play
 * (`https://play.google.com/store/apps/details?id=…`) est d'abord ouvert dans l'application Play
 * Store (`market://`), à défaut dans le navigateur. Tout autre lien HTTPS va au navigateur.
 *
 * @return `false` si rien n'a pu l'ouvrir (l'écran affiche alors un message).
 */
object UpdateLinkOpener {

    private const val PLAY_HOST = "play.google.com"

    fun open(context: Context, url: String): Boolean {
        val uri = Uri.parse(url)
        val packageId = uri.getQueryParameter("id")
        if (uri.host == PLAY_HOST && uri.path == "/store/apps/details" && !packageId.isNullOrBlank()) {
            if (start(context, Uri.parse("market://details?id=$packageId"))) return true
        }
        return start(context, uri)
    }

    private fun start(context: Context, uri: Uri): Boolean = WebLinkOpener.open(context, uri)
}
