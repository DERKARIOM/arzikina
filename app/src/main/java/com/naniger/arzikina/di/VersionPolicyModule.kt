package com.naniger.arzikina.di

import com.naniger.arzikina.data.versioning.InstalledVersionProviderImpl
import com.naniger.arzikina.data.versioning.UpgradeRequiredSignalImpl
import com.naniger.arzikina.data.versioning.VersionPolicyRepositoryImpl
import com.naniger.arzikina.domain.versioning.InstalledVersionProvider
import com.naniger.arzikina.domain.versioning.UpgradeRequiredSignal
import com.naniger.arzikina.domain.versioning.VersionPolicyRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Contrôle de version et mises à jour obligatoires (voir `domain/versioning`). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class VersionPolicyModule {

    @Binds
    @Singleton
    abstract fun bindVersionPolicyRepository(impl: VersionPolicyRepositoryImpl): VersionPolicyRepository

    @Binds
    @Singleton
    abstract fun bindInstalledVersionProvider(impl: InstalledVersionProviderImpl): InstalledVersionProvider

    @Binds
    @Singleton
    abstract fun bindUpgradeRequiredSignal(impl: UpgradeRequiredSignalImpl): UpgradeRequiredSignal
}
