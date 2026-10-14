package com.naniger.arzikina.data.remote.interceptor

import com.naniger.arzikina.data.remote.RemoteConfig
import com.naniger.arzikina.domain.versioning.InstalledVersionProvider
import com.naniger.arzikina.domain.versioning.UpgradeRequiredSignal
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Câblé sur le client OkHttp de BASE : les clients dérivés (`@SyncHttpClient`) en héritent via
 * `newBuilder()`, donc aucune requête vers l'API Arzikina n'y échappe.
 *
 * - Sortie : pose `X-App-Version-Code` (entier) — le serveur s'en sert pour refuser les opérations
 *   protégées aux versions obsolètes. Uniquement vers l'hôte de l'API, jamais vers un tiers.
 * - Retour : un HTTP 426 (`upgrade_required`) est signalé via [UpgradeRequiredSignal]. La réponse
 *   n'est ni modifiée ni consommée : l'appelant la traite comme n'importe quelle erreur serveur.
 */
class AppVersionInterceptor @Inject constructor(
    private val installedVersionProvider: InstalledVersionProvider,
    private val upgradeRequiredSignal: UpgradeRequiredSignal
) : Interceptor {

    private val apiHost: String = RemoteConfig.BASE_URL.toHttpUrl().host

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.url.host != apiHost) return chain.proceed(original)

        val request = original.newBuilder()
            .header(HEADER_VERSION_CODE, installedVersionProvider.installedVersion().versionCode.toString())
            .build()
        val response = chain.proceed(request)
        if (response.code == HTTP_UPGRADE_REQUIRED) {
            upgradeRequiredSignal.notifyUpgradeRequired()
        }
        return response
    }

    private companion object {
        const val HEADER_VERSION_CODE = "X-App-Version-Code"
        const val HTTP_UPGRADE_REQUIRED = 426
    }
}
