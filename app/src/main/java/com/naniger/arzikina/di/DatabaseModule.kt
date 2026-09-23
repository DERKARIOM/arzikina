package com.naniger.arzikina.di

import android.content.Context
import androidx.room.Room
import com.naniger.arzikina.data.local.dao.AccountDao
import com.naniger.arzikina.data.local.dao.BudgetDao
import com.naniger.arzikina.data.local.dao.CardSecretDao
import com.naniger.arzikina.data.local.dao.CategoryDao
import com.naniger.arzikina.data.local.dao.FinancialPlanDao
import com.naniger.arzikina.data.local.dao.FinancialPlanItemDao
import com.naniger.arzikina.data.local.dao.LoanDao
import com.naniger.arzikina.data.local.dao.LoanPaymentDao
import com.naniger.arzikina.data.local.dao.PersonDao
import com.naniger.arzikina.data.local.dao.ReceiptDao
import com.naniger.arzikina.data.local.dao.RecurringTransactionDao
import com.naniger.arzikina.data.local.dao.RecurringTransactionOccurrenceDao
import com.naniger.arzikina.data.local.dao.SavingsGoalDao
import com.naniger.arzikina.data.local.dao.SyncQueueDao
import com.naniger.arzikina.data.local.dao.TransactionDao
import com.naniger.arzikina.data.local.dao.TransactionTemplateDao
import com.naniger.arzikina.data.local.dao.UserDao
import com.naniger.arzikina.data.local.dao.UserPreferencesDao
import com.naniger.arzikina.data.local.dao.UserProfilePhotoDao
import com.naniger.arzikina.data.local.dao.UserServerLinkDao
import com.naniger.arzikina.data.local.database.ArzikinaDatabase
import com.naniger.arzikina.data.local.database.MIGRATION_1_2
import com.naniger.arzikina.data.local.database.MIGRATION_2_3
import com.naniger.arzikina.data.local.database.MIGRATION_3_4
import com.naniger.arzikina.data.local.database.MIGRATION_4_5
import com.naniger.arzikina.data.local.database.MIGRATION_5_6
import com.naniger.arzikina.data.local.database.MIGRATION_6_7
import com.naniger.arzikina.data.local.database.MIGRATION_7_8
import com.naniger.arzikina.data.local.database.MIGRATION_8_9
import com.naniger.arzikina.data.local.database.MIGRATION_9_10
import com.naniger.arzikina.data.local.database.MIGRATION_10_11
import com.naniger.arzikina.data.local.database.MIGRATION_11_12
import com.naniger.arzikina.data.local.database.MIGRATION_12_13
import com.naniger.arzikina.data.local.database.MIGRATION_13_14
import com.naniger.arzikina.data.local.database.MIGRATION_14_15
import com.naniger.arzikina.data.local.database.MIGRATION_15_16
import com.naniger.arzikina.data.local.database.MIGRATION_16_17
import com.naniger.arzikina.data.local.database.MIGRATION_17_18
import com.naniger.arzikina.data.local.database.MIGRATION_18_19
import com.naniger.arzikina.data.local.database.MIGRATION_19_20
import com.naniger.arzikina.data.local.database.MIGRATION_20_21
import com.naniger.arzikina.data.local.database.MIGRATION_21_22
import com.naniger.arzikina.data.local.database.MIGRATION_22_23
import com.naniger.arzikina.data.local.database.MIGRATION_23_24
import com.naniger.arzikina.data.local.database.MIGRATION_24_25
import com.naniger.arzikina.data.local.database.MIGRATION_25_26
import com.naniger.arzikina.data.local.database.MIGRATION_26_27
import com.naniger.arzikina.data.local.database.MIGRATION_27_28
import com.naniger.arzikina.data.local.database.MIGRATION_28_29
import com.naniger.arzikina.data.local.database.MIGRATION_29_30
import com.naniger.arzikina.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Fournit la base de données Room et les DAO qui en découlent.
 *
 * Pas de peuplement de données par défaut ici (contrairement à avant
 * l'authentification) : les comptes/catégories par défaut appartiennent
 * désormais à un utilisateur précis (voir `data/local/database/DefaultAccounts`
 * et `DefaultCategories`, dont `seed()` exige un `userId`) — une base neuve
 * n'a par définition encore aucun utilisateur. Ce peuplement se déclenche
 * maintenant juste après l'inscription (voir la feuille de route
 * Authentification, écran Inscription), pas à la création de la base.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): ArzikinaDatabase =
        Room.databaseBuilder(context, ArzikinaDatabase::class.java, Constants.DATABASE_NAME)
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
                MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18,
                MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22,
                MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26,
                MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30
            )
            .build()

    @Provides
    fun provideAccountDao(database: ArzikinaDatabase): AccountDao = database.accountDao()

    @Provides
    fun provideCategoryDao(database: ArzikinaDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideTransactionDao(database: ArzikinaDatabase): TransactionDao = database.transactionDao()

    @Provides
    fun provideBudgetDao(database: ArzikinaDatabase): BudgetDao = database.budgetDao()

    @Provides
    fun provideSavingsGoalDao(database: ArzikinaDatabase): SavingsGoalDao = database.savingsGoalDao()

    @Provides
    fun provideUserDao(database: ArzikinaDatabase): UserDao = database.userDao()

    @Provides
    fun provideCardSecretDao(database: ArzikinaDatabase): CardSecretDao = database.cardSecretDao()

    @Provides
    fun providePersonDao(database: ArzikinaDatabase): PersonDao = database.personDao()

    @Provides
    fun provideLoanDao(database: ArzikinaDatabase): LoanDao = database.loanDao()

    @Provides
    fun provideLoanPaymentDao(database: ArzikinaDatabase): LoanPaymentDao = database.loanPaymentDao()

    @Provides
    fun provideRecurringTransactionDao(database: ArzikinaDatabase): RecurringTransactionDao =
        database.recurringTransactionDao()

    @Provides
    fun provideRecurringTransactionOccurrenceDao(database: ArzikinaDatabase): RecurringTransactionOccurrenceDao =
        database.recurringTransactionOccurrenceDao()

    @Provides
    fun provideFinancialPlanDao(database: ArzikinaDatabase): FinancialPlanDao = database.financialPlanDao()

    @Provides
    fun provideFinancialPlanItemDao(database: ArzikinaDatabase): FinancialPlanItemDao = database.financialPlanItemDao()

    @Provides
    fun provideReceiptDao(database: ArzikinaDatabase): ReceiptDao = database.receiptDao()

    @Provides
    fun provideSyncQueueDao(database: ArzikinaDatabase): SyncQueueDao = database.syncQueueDao()

    @Provides
    fun provideUserPreferencesDao(database: ArzikinaDatabase): UserPreferencesDao = database.userPreferencesDao()

    @Provides
    fun provideUserServerLinkDao(database: ArzikinaDatabase): UserServerLinkDao = database.userServerLinkDao()

    @Provides
    fun provideUserProfilePhotoDao(database: ArzikinaDatabase): UserProfilePhotoDao = database.userProfilePhotoDao()

    @Provides
    fun provideTransactionTemplateDao(database: ArzikinaDatabase): TransactionTemplateDao =
        database.transactionTemplateDao()
}
