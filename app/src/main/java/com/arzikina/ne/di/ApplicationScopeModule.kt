package com.arzikina.ne.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Portée de coroutine liée au cycle de vie de l'application entière (ex.
 * peupler la base au premier lancement, depuis un `RoomDatabase.Callback`
 * qui n'a pas de `viewModelScope` à sa disposition).
 *
 * À ne jamais utiliser pour du travail lié à un écran : les ViewModels
 * utilisent leur propre `viewModelScope`, annulé automatiquement quand
 * l'écran disparaît.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object ApplicationScopeModule {

    @ApplicationScope
    @Provides
    @Singleton
    fun providesApplicationScope(
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher
    ): CoroutineScope = CoroutineScope(SupervisorJob() + defaultDispatcher)
}
