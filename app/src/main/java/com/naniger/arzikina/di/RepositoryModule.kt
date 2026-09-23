package com.naniger.arzikina.di

import com.naniger.arzikina.data.repository.AccountRepositoryImpl
import com.naniger.arzikina.data.repository.AuthRepositoryImpl
import com.naniger.arzikina.data.repository.BackupRepositoryImpl
import com.naniger.arzikina.data.security.BiometricAuthenticatorImpl
import com.naniger.arzikina.data.repository.BudgetRepositoryImpl
import com.naniger.arzikina.data.repository.CategoryRepositoryImpl
import com.naniger.arzikina.data.repository.FinancialPlanRepositoryImpl
import com.naniger.arzikina.data.repository.LoanRepositoryImpl
import com.naniger.arzikina.data.repository.PersonRepositoryImpl
import com.naniger.arzikina.data.repository.ProfilePhotoRepositoryImpl
import com.naniger.arzikina.data.repository.ReceiptRepositoryImpl
import com.naniger.arzikina.data.repository.RecurringTransactionRepositoryImpl
import com.naniger.arzikina.data.repository.SavingsGoalRepositoryImpl
import com.naniger.arzikina.data.repository.SessionManagerImpl
import com.naniger.arzikina.data.repository.SyncAuthRepositoryImpl
import com.naniger.arzikina.data.repository.SyncEngineImpl
import com.naniger.arzikina.data.repository.TokenProvider
import com.naniger.arzikina.data.repository.TokenProviderImpl
import com.naniger.arzikina.data.repository.TransactionRepositoryImpl
import com.naniger.arzikina.data.repository.TransactionTemplateRepositoryImpl
import com.naniger.arzikina.data.repository.UnifiedAuthRepositoryImpl
import com.naniger.arzikina.data.repository.UserPreferencesRepositoryImpl
import com.naniger.arzikina.work.AutomationSchedulerImpl
import com.naniger.arzikina.domain.repository.AccountRepository
import com.naniger.arzikina.domain.repository.AuthRepository
import com.naniger.arzikina.domain.repository.AutomationScheduler
import com.naniger.arzikina.domain.repository.BackupRepository
import com.naniger.arzikina.domain.repository.BiometricAuthenticator
import com.naniger.arzikina.domain.repository.BudgetRepository
import com.naniger.arzikina.domain.repository.CategoryRepository
import com.naniger.arzikina.domain.repository.FinancialPlanRepository
import com.naniger.arzikina.domain.repository.LoanRepository
import com.naniger.arzikina.domain.repository.PersonRepository
import com.naniger.arzikina.domain.repository.ProfilePhotoRepository
import com.naniger.arzikina.domain.repository.ReceiptRepository
import com.naniger.arzikina.domain.repository.RecurringTransactionRepository
import com.naniger.arzikina.domain.repository.SavingsGoalRepository
import com.naniger.arzikina.domain.repository.SessionManager
import com.naniger.arzikina.domain.repository.SyncAuthRepository
import com.naniger.arzikina.domain.repository.SyncEngine
import com.naniger.arzikina.domain.repository.TransactionRepository
import com.naniger.arzikina.domain.repository.TransactionTemplateRepository
import com.naniger.arzikina.domain.repository.UnifiedAuthRepository
import com.naniger.arzikina.domain.repository.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Relie chaque interface de repository du domaine à son implémentation Room.
 * Ajouter une fonctionnalité (Transactions, Budgets...) se limite à ajouter
 * une méthode `@Binds` ici, sans toucher au reste du graphe de dépendances.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindBudgetRepository(impl: BudgetRepositoryImpl): BudgetRepository

    @Binds
    @Singleton
    abstract fun bindSavingsGoalRepository(impl: SavingsGoalRepositoryImpl): SavingsGoalRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(impl: UserPreferencesRepositoryImpl): UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSessionManager(impl: SessionManagerImpl): SessionManager

    @Binds
    @Singleton
    abstract fun bindPersonRepository(impl: PersonRepositoryImpl): PersonRepository

    @Binds
    @Singleton
    abstract fun bindLoanRepository(impl: LoanRepositoryImpl): LoanRepository

    @Binds
    @Singleton
    abstract fun bindRecurringTransactionRepository(impl: RecurringTransactionRepositoryImpl): RecurringTransactionRepository

    @Binds
    @Singleton
    abstract fun bindBiometricAuthenticator(impl: BiometricAuthenticatorImpl): BiometricAuthenticator

    @Binds
    @Singleton
    abstract fun bindFinancialPlanRepository(impl: FinancialPlanRepositoryImpl): FinancialPlanRepository

    @Binds
    @Singleton
    abstract fun bindAutomationScheduler(impl: AutomationSchedulerImpl): AutomationScheduler

    @Binds
    @Singleton
    abstract fun bindReceiptRepository(impl: ReceiptRepositoryImpl): ReceiptRepository

    /** Connexion au serveur de synchronisation (voir `domain/repository/SyncAuthRepository.kt`) —
     *  à ne pas confondre avec [bindAuthRepository] ci-dessus (authentification LOCALE). */
    @Binds
    @Singleton
    abstract fun bindSyncAuthRepository(impl: SyncAuthRepositoryImpl): SyncAuthRepository

    /** Partage le même stockage que [bindSyncAuthRepository] via `SyncAuthStore` (voir la KDoc de
     *  [TokenProviderImpl]) — contrat interne à la couche data, réservé au futur intercepteur
     *  réseau. Classe séparée de [SyncAuthRepositoryImpl] (une seule instance par interface :
     *  Single Responsibility, voir `SyncAuthStore` pour la logique partagée). */
    @Binds
    @Singleton
    abstract fun bindTokenProvider(impl: TokenProviderImpl): TokenProvider

    /** Voir la doc de tête de [SyncEngine] et [SyncEngineImpl] — aucun appelant à cette étape
     *  (fondation posée à l'avance), le déclenchement viendra dans une étape dédiée. */
    @Binds
    @Singleton
    abstract fun bindSyncEngine(impl: SyncEngineImpl): SyncEngine

    /** Login unifié (étape D du chantier "audit auth + sync + doublons") — voir la KDoc de
     *  [UnifiedAuthRepository], point d'entrée désormais unique pour la présentation
     *  (`presentation/auth`), au-dessus de [bindAuthRepository]/[bindSyncAuthRepository]. */
    @Binds
    @Singleton
    abstract fun bindUnifiedAuthRepository(impl: UnifiedAuthRepositoryImpl): UnifiedAuthRepository

    /** Cahier des charges "Gestion de la photo de profil" — voir la KDoc de [ProfilePhotoRepository]
     *  pour pourquoi c'est un repository séparé de [bindAuthRepository]. */
    @Binds
    @Singleton
    abstract fun bindProfilePhotoRepository(impl: ProfilePhotoRepositoryImpl): ProfilePhotoRepository

    /** Cahier des charges "Marketplace personnelle" — voir la KDoc de [TransactionTemplateRepository]
     *  (bibliothèque personnelle de modèles de transaction, aucun lien avec une marketplace publique). */
    @Binds
    @Singleton
    abstract fun bindTransactionTemplateRepository(impl: TransactionTemplateRepositoryImpl): TransactionTemplateRepository
}
