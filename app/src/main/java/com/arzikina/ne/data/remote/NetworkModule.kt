package com.arzikina.ne.data.remote

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Câblage réseau (OkHttp) pour l'API de synchronisation Arzikina — voir
 * docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md, section 7. Fondation partagée par tous les futurs
 * appels serveur.
 *
 * PAS de Retrofit (voir la KDoc de [com.arzikina.ne.data.remote.api.SyncAuthApi] pour le
 * diagnostic complet) : un `@Provides` retournant une interface via `retrofit.create()` suffit
 * seul à faire échouer KSP/Dagger dans ce toolchain — confirmé aussi bien avec Retrofit 3.0.0
 * qu'avec 2.11.0. Ce module ne fournit donc que des types concrets ([Json], [OkHttpClient]),
 * consommés par des classes `@Inject`-constructibles (voir `SyncAuthApi`) plutôt que par des
 * interfaces fournies via `@Provides`.
 *
 * Réutilise `kotlinx.serialization` (déjà présent pour la sauvegarde/restauration, voir
 * `data/backup/`) plutôt que d'ajouter une deuxième librairie JSON (Gson/Moshi) — évite le code
 * dupliqué et une dépendance de plus (voir instructions projet).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TIMEOUT_SECONDS = 15L

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // Tolère l'ajout futur de champs côté serveur sans casser un client déjà installé —
        // important dès qu'on aura des appareils sur des versions d'app différentes en même temps.
        ignoreUnknownKeys = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
}
