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
import javax.inject.Singleton

private const val PREFERENCES_DATASTORE_NAME = "arzikina_preferences"

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PREFERENCES_DATASTORE_NAME
)

/**
 * Fournit le [DataStore] de préférences utilisateur (thème, devise
 * principale...), séparé de la base Room : ce sont deux mécanismes de
 * persistance différents pour deux besoins différents (voir
 * [com.arzikina.ne.data.repository.UserPreferencesRepositoryImpl]).
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.preferencesDataStore
}
