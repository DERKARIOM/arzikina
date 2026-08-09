package com.arzikina.ne.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.arzikina.ne.data.local.dao.AccountDao
import com.arzikina.ne.data.local.dao.BudgetDao
import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.local.dao.SavingsGoalDao
import com.arzikina.ne.data.local.dao.TransactionDao
import com.arzikina.ne.data.local.dao.UserDao
import com.arzikina.ne.data.local.entity.AccountEntity
import com.arzikina.ne.data.local.entity.BudgetEntity
import com.arzikina.ne.data.local.entity.CategoryEntity
import com.arzikina.ne.data.local.entity.SavingsGoalEntity
import com.arzikina.ne.data.local.entity.TransactionEntity
import com.arzikina.ne.data.local.entity.UserEntity

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
 * Le peuplement des comptes/catégories par défaut n'a plus lieu à la
 * création de la base (voir `di/DatabaseModule`) : il se déclenche
 * désormais après l'inscription d'un utilisateur (voir `DefaultAccounts`,
 * `DefaultCategories` et la feuille de route Authentification), puisque ces
 * données par défaut appartiennent maintenant à un utilisateur précis.
 *
 * Historique des versions :
 * - 1 : Comptes.
 * - 2 : Catégories (voir [MIGRATION_1_2]).
 * - 3 : Transactions (voir [MIGRATION_2_3]).
 * - 4 : Budgets (voir [MIGRATION_3_4]).
 * - 5 : Objectifs d'épargne (voir [MIGRATION_4_5]).
 * - 6 : Authentification, table `users` (voir [MIGRATION_5_6]).
 * - 7 : Isolation multi-utilisateurs, colonne `userId` sur les 5 tables
 *   existantes + rattachement des données préexistantes à un utilisateur
 *   par défaut (voir [MIGRATION_6_7]).
 * - 8 : Question de sécurité (`securityQuestion` / `securityAnswerHash`),
 *   pour la réinitialisation locale du mot de passe (voir [MIGRATION_7_8]).
 * - 9 : Moyen de paiement optionnel sur une transaction (`paymentMethod`,
 *   voir [MIGRATION_8_9]).
 * - 10 : Transfert entre deux comptes (`transferAccountId`, `categoryId`
 *   devient optionnel — voir [MIGRATION_9_10]).
 * - 11 : Compte de type Carte de crédit (`type`, `cardLastFourDigits`,
 *   `cardExpiryMonth`, `cardExpiryYear` sur `accounts` — voir [MIGRATION_10_11]).
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        SavingsGoalEntity::class,
        UserEntity::class
    ],
    version = 11,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ArzikinaDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun userDao(): UserDao
}
