package com.arzikina.ne.work

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Déclenche [SyncWorkScheduler.triggerNow] dès qu'une connexion réseau redevient disponible — voir
 * sa doc pour le raisonnement complet (réactivité, complément du filet de sécurité périodique).
 *
 * [Singleton]/[start] appelé UNE SEULE FOIS depuis [com.arzikina.ne.ArzikinaApplication.onCreate]
 * (jamais depuis un Fragment/ViewModel, même principe que [RecurringOccurrencesScheduler]) : un
 * `NetworkCallback` enregistré doit vivre aussi longtemps que le PROCESSUS, pas un écran — l'inverse
 * (enregistrer/désenregistrer à chaque Fragment) manquerait des changements de connectivité survenus
 * app fermée.
 *
 * `NET_CAPABILITY_INTERNET` (pas `NET_CAPABILITY_VALIDATED`) : suffisant ici — [SyncWorkScheduler]
 * pose de toute façon sa propre contrainte `NetworkType.CONNECTED` sur le `WorkRequest` déclenché,
 * qui filtre déjà les faux positifs (réseau annoncé disponible mais finalement inutilisable) avant
 * toute tentative réseau réelle.
 */
@Singleton
class SyncConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            SyncWorkScheduler.triggerNow(context)
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager?.registerNetworkCallback(request, networkCallback)
    }
}
