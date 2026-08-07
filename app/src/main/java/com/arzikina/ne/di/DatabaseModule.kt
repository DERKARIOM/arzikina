package com.arzikina.ne.di

import android.content.Context
import androidx.room.Room
import com.arzikina.ne.data.local.dao.AccountDao
import com.arzikina.ne.data.local.dao.BudgetDao
import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.local.dao.SavingsGoalDao
import com.arzikina.ne.data.local.dao.TransactionDao
import com.arzikina.ne.data.local.dao.UserDao
import com.arzikina.ne.data.local.database.ArzikinaDatabase
import com.arzikina.ne.data.local.database.MIGRATION_1_2
import com.arzikina.ne.data.local.database.MIGRATION_2_3
import com.arzikina.ne.data.local.database.MIGRATION_3_4
import com.arzikina.ne.data.local.database.MIGRATION_4_5
import com.arzikina.ne.data.local.database.MIGRATION_5_6
import com.arzikina.ne.data.local.database.MIGRATION_6_7
import com.arzikina.ne.data.local.database.MIGRATION_7_8
import com.arzikina.ne.util.Constants
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
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
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
}
