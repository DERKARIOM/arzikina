package com.naniger.arzikina.di

import com.naniger.arzikina.data.push.FirebasePushTokenSource
import com.naniger.arzikina.data.push.PushTokenSource
import com.naniger.arzikina.data.repository.PushRegistrationRepositoryImpl
import com.naniger.arzikina.domain.repository.PushRegistrationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Câblage des notifications push (Firebase Cloud Messaging). Module séparé de `RepositoryModule`
 * pour regrouper la fonctionnalité : la retirer ou la remplacer (autre fournisseur de push) ne
 * touche que ce fichier.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PushModule {

    @Binds
    @Singleton
    abstract fun bindPushRegistrationRepository(impl: PushRegistrationRepositoryImpl): PushRegistrationRepository

    @Binds
    abstract fun bindPushTokenSource(impl: FirebasePushTokenSource): PushTokenSource
}
