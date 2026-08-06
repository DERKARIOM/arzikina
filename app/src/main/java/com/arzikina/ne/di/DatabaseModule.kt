package com.arzikina.ne.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arzikina.ne.data.local.dao.AccountDao
import com.arzikina.ne.data.local.dao.BudgetDao
import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.local.dao.SavingsGoalDao
import com.arzikina.ne.data.local.dao.TransactionDao
import com.arzikina.ne.data.local.database.ArzikinaDatabase
import com.arzikina.ne.data.local.database.DefaultAccounts
import com.arzikina.ne.data.local.database.DefaultCategories
import com.arzikina.ne.data.local.database.MIGRATION_1_2
import com.arzikina.ne.data.local.database.MIGRATION_2_3
import com.arzikina.ne.data.local.database.MIGRATION_3_4
import com.arzikina.ne.data.local.database.MIGRATION_4_5
import com.arzikina.ne.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Fournit la base de données Room et les DAO qui en découlent.
 *
 * Le peuplement des données par défaut passe par des [Provider] de DAO
 * plutôt qu'une injection directe : au moment où le callback de création de
 * la base est construit, la base elle-même n'existe pas encore. Un
 * [Provider] retarde la résolution jusqu'au premier appel réel de `.get()`,
 * qui n'arrive qu'après la fin de la construction — cela évite une
 * dépendance circulaire tout en gardant l'injection de dépendances propre.
 *
 * `onCreate` ne se déclenche que pour une toute nouvelle installation (pas
 * de fichier de base existant) : les migrations (ex. [MIGRATION_1_2]) gèrent
 * leur propre peuplement pour les mises à jour, voir leurs commentaires.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        accountDaoProvider: Provider<AccountDao>,
        categoryDaoProvider: Provider<CategoryDao>,
        @ApplicationScope appScope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): ArzikinaDatabase =
        Room.databaseBuilder(context, ArzikinaDatabase::class.java, Constants.DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    val now = System.currentTimeMillis()
                    appScope.launch(ioDispatcher) {
                        accountDaoProvider.get().insertAll(DefaultAccounts.seed(now))
                        categoryDaoProvider.get().insertAll(DefaultCategories.seed(now))
                    }
                }
            })
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
}
