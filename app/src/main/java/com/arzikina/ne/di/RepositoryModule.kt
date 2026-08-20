package com.arzikina.ne.di

import com.arzikina.ne.data.repository.AccountRepositoryImpl
import com.arzikina.ne.data.repository.AuthRepositoryImpl
import com.arzikina.ne.data.repository.BackupRepositoryImpl
import com.arzikina.ne.data.security.BiometricAuthenticatorImpl
import com.arzikina.ne.data.repository.BudgetRepositoryImpl
import com.arzikina.ne.data.repository.CategoryRepositoryImpl
import com.arzikina.ne.data.repository.FinancialPlanRepositoryImpl
import com.arzikina.ne.data.repository.LoanRepositoryImpl
import com.arzikina.ne.data.repository.PersonRepositoryImpl
import com.arzikina.ne.data.repository.ReceiptRepositoryImpl
import com.arzikina.ne.data.repository.RecurringTransactionRepositoryImpl
import com.arzikina.ne.data.repository.SavingsGoalRepositoryImpl
import com.arzikina.ne.data.repository.SessionManagerImpl
import com.arzikina.ne.data.repository.TransactionRepositoryImpl
import com.arzikina.ne.data.repository.UserPreferencesRepositoryImpl
import com.arzikina.ne.work.AutomationSchedulerImpl
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.AuthRepository
import com.arzikina.ne.domain.repository.AutomationScheduler
import com.arzikina.ne.domain.repository.BackupRepository
import com.arzikina.ne.domain.repository.BiometricAuthenticator
import com.arzikina.ne.domain.repository.BudgetRepository
import com.arzikina.ne.domain.repository.CategoryRepository
import com.arzikina.ne.domain.repository.FinancialPlanRepository
import com.arzikina.ne.domain.repository.LoanRepository
import com.arzikina.ne.domain.repository.PersonRepository
import com.arzikina.ne.domain.repository.ReceiptRepository
import com.arzikina.ne.domain.repository.RecurringTransactionRepository
import com.arzikina.ne.domain.repository.SavingsGoalRepository
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.TransactionRepository
import com.arzikina.ne.domain.repository.UserPreferencesRepository
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
}
