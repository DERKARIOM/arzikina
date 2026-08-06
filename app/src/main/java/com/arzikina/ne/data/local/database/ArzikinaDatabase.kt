package com.arzikina.ne.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.arzikina.ne.data.local.dao.AccountDao
import com.arzikina.ne.data.local.dao.BudgetDao
import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.local.dao.SavingsGoalDao
import com.arzikina.ne.data.local.dao.TransactionDao
import com.arzikina.ne.data.local.entity.AccountEntity
import com.arzikina.ne.data.local.entity.BudgetEntity
import com.arzikina.ne.data.local.entity.CategoryEntity
import com.arzikina.ne.data.local.entity.SavingsGoalEntity
import com.arzikina.ne.data.local.entity.TransactionEntity

/**
 * Base de données locale unique de l'application (SQLite via Room).
 *
 * `exportSchema = true` : l'historique des schémas est conservé dans
 * `app/schemas` afin d'écrire des migrations fiables à chaque évolution.
 * Chaque nouvelle fonctionnalité (Transactions, Budgets, Objectifs
 * d'épargne...) ajoute sa propre entité, incrémente [version] et fournit sa
 * propre `Migration` (voir `di/DatabaseModule`), sans jamais modifier les
 * entités existantes en place.
 *
 * Le peuplement des données par défaut au premier lancement se fait via un
 * `RoomDatabase.Callback` déclaré dans `di/DatabaseModule` (et non ici) pour
 * garder cette classe strictement limitée à la définition du schéma.
 *
 * Historique des versions :
 * - 1 : Comptes.
 * - 2 : Catégories (voir [MIGRATION_1_2]).
 * - 3 : Transactions (voir [MIGRATION_2_3]).
 * - 4 : Budgets (voir [MIGRATION_3_4]).
 * - 5 : Objectifs d'épargne (voir [MIGRATION_4_5]).
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        SavingsGoalEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ArzikinaDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun savingsGoalDao(): SavingsGoalDao
}
