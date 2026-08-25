package com.arzikina.ne.data.remote.interceptor

import com.arzikina.ne.data.repository.TokenProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Pose l'en-tête `Authorization: Bearer <token>` sur les requêtes sous `api/sync/` — l'intercepteur
 * annoncé par la KDoc de [TokenProvider] depuis l'étape "connexion au serveur" (voir sa doc pour le
 * raisonnement complet sur ce découpage). Câblé UNIQUEMENT sur le client HTTP qualifié
 * `@SyncHttpClient` (voir `NetworkModule`), jamais sur le client partagé par
 * [com.arzikina.ne.data.remote.api.SyncAuthApi] : `login.php` n'a justement pas encore de token à
 * poser (c'est cette route qui en délivre un).
 *
 * `runBlocking` : pattern standard pour un intercepteur OkHttp qui a besoin d'une lecture suspend
 * (ici DataStore, via [TokenProvider]) — les intercepteurs s'exécutent déjà hors du thread
 * principal (OkHttp les appelle sur son propre pool de threads réseau), donc ce blocage est sans
 * risque ici, contrairement à un `runBlocking` posé sur le thread principal.
 *
 * Ne bloque JAMAIS la requête si aucun token valide n'est disponible (utilisateur non connecté,
 * token expiré) : la laisse partir sans en-tête — le serveur répondra 401, interprété par l'appelant
 * (voir le futur `SyncEngineImpl`) exactement comme une erreur serveur normale, pas un cas
 * particulier à gérer ici.
 */
class SyncAuthInterceptor @Inject constructor(
    private val tokenProvider: TokenProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = runBlocking { tokenProvider.getValidRawToken() }
            ?: return chain.proceed(request)

        val authenticatedRequest = request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authenticatedRequest)
    }
}
