package com.naniger.arzikina.di

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.naniger.arzikina.data.update.InAppUpdatePromptStoreImpl
import com.naniger.arzikina.domain.repository.InAppUpdatePromptStore
import com.naniger.arzikina.domain.update.InAppUpdateConfig
import com.naniger.arzikina.domain.update.InAppUpdatePolicy
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Google Play In-App Updates (voir `presentation/update/PlayStoreUpdateManager.kt`).
 *
 * [AppUpdateManager] fourni ici plutôt que créé dans le gestionnaire : un test instrumenté peut le
 * remplacer par le `FakeAppUpdateManager` officiel (`@TestInstallIn`) pour simuler chaque scénario
 * (mise à jour disponible, téléchargement, échec…) sans passer par le Play Store.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppUpdateModule {

    @Binds
    @Singleton
    abstract fun bindInAppUpdatePromptStore(impl: InAppUpdatePromptStoreImpl): InAppUpdatePromptStore

    companion object {

        @Provides
        @Singleton
        fun provideAppUpdateManager(@ApplicationContext context: Context): AppUpdateManager =
            AppUpdateManagerFactory.create(context)

        /** Stratégie de mise à jour : modifier [InAppUpdateConfig] pour ajuster les seuils. */
        @Provides
        @Singleton
        fun provideInAppUpdatePolicy(): InAppUpdatePolicy = InAppUpdatePolicy(InAppUpdateConfig())
    }
}
