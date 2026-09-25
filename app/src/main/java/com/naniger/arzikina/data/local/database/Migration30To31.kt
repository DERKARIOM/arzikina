package com.naniger.arzikina.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v30 → v31 : Objectif d'épargne devenu un TYPE de compte (`AccountType.SAVINGS_GOAL`, voir sa doc).
 *
 * Deux colonnes NULLABLES sans défaut (même principe que `cardLastFourDigits`/
 * `mobileMoneyPackageName`) : `NULL` pour toutes les lignes existantes, aucun compte actuel n'est
 * un objectif. Le type lui-même est déjà stocké en texte (`type`), aucune modification nécessaire.
 *
 * Volontairement SANS conversion des anciens objectifs (`savings_goals`) ici : cette conversion doit
 * aussi enfiler leur synchronisation (`sync_queue`, payload JSON), ce qu'une migration SQL ne sait
 * pas faire proprement — voir `data/repository/LegacySavingsGoalMigrator`, exécuté au démarrage et après chaque pull.
 * Même équivalent côté serveur : `database/migrations/006_savings_goal_accounts.sql`.
 */
val MIGRATION_30_31 = object : Migration(startVersion = 30, endVersion = 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `savingsTargetAmount` INTEGER")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `savingsDescription` TEXT")
    }
}
