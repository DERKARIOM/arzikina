package com.arzikina.ne.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

private const val PREFERENCES_DATASTORE_NAME = "arzikina_preferences"
private const val SESSION_DATASTORE_NAME = "arzikina_session"

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PREFERENCES_DATASTORE_NAME
)

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SESSION_DATASTORE_NAME
)

/**
 * Distingue les deux [DataStore]<[Preferences]> du graphe Hilt (même type,
 * deux fichiers/usages différents) : sans qualifier, Dagger ne saurait lequel
 * injecter. Voir [com.arzikina.ne.data.repository.SessionManagerImpl].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SessionDataStore

/**
 * Fournit les [DataStore] de préférences utilisateur (thème, devise
 * principale...) et de session (voir [SessionDataStore]) — deux fichiers
 * distincts : la session appartient au module Authentification, les
 * préférences d'affichage à un autre domaine fonctionnel, même si le
 * mécanisme technique est identique. Séparés de la base Room : ce sont deux
 * mécanismes de persistance différents pour deux besoins différents.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.preferencesDataStore

    @SessionDataStore
    @Provides
    @Singleton
    fun provideSessionDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.sessionDataStore
}
