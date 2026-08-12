package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 13 → 14 : fonctionnalité Transactions récurrentes / planifiées.
 *
 * Deux nouvelles tables, dont le SQL reproduit EXACTEMENT le schéma que Room déduit des entités
 * `RecurringTransactionEntity`/`RecurringTransactionOccurrenceEntity` (mêmes noms d'index que la
 * convention automatique `index_<table>_<colonne>`, voir [MIGRATION_12_13]) :
 * - `recurring_transactions` : la RÈGLE (montant, compte, catégorie, fréquence...), `FOREIGN KEY
 *   ... ON DELETE CASCADE` vers `accounts` (même principe que `transactions.accountId`), sans
 *   contrainte vers `categories` (nullable, voir [MIGRATION_9_10] pour le même choix sur
 *   `transactions.categoryId`).
 * - `recurring_transaction_occurrences` : l'exécution réelle d'une règle à une date donnée,
 *   `FOREIGN KEY ... ON DELETE CASCADE` vers `recurring_transactions`. Index UNIQUE sur
 *   `(recurringTransactionId, scheduledDate)` : protection base contre une double génération de la
 *   même échéance (voir la doc de `RecurringTransactionOccurrenceEntity`). Pas de contrainte vers
 *   `transactions` (même raisonnement que `loan_payments.transactionId`, voir [MIGRATION_12_13]).
 *
 * Aucun backfill nécessaire : contrairement à Prêts/Emprunts (catégories par défaut), cette
 * fonctionnalité ne repose sur aucune donnée préexistante à créer pour les utilisateurs déjà là.
 */
val MIGRATION_13_14 = object : Migration(startVersion = 13, endVersion = 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring_transactions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` INTEGER NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`amount` INTEGER NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`categoryId` INTEGER, " +
                "`description` TEXT NOT NULL, " +
                "`paymentMethod` TEXT, " +
                "`startDate` INTEGER NOT NULL, " +
                "`endDate` INTEGER, " +
                "`frequency` TEXT NOT NULL, " +
                "`nextExecutionDate` INTEGER NOT NULL, " +
                "`isActive` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_transactions_accountId` ON `recurring_transactions` (`accountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_transactions_categoryId` ON `recurring_transactions` (`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_transactions_userId` ON `recurring_transactions` (`userId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring_transaction_occurrences` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` INTEGER NOT NULL, " +
                "`recurringTransactionId` INTEGER NOT NULL, " +
                "`scheduledDate` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`transactionId` INTEGER, " +
                "`processedAt` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`recurringTransactionId`) REFERENCES `recurring_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_transaction_occurrences_userId` ON `recurring_transaction_occurrences` (`userId`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_transaction_occurrences_recurringTransactionId_scheduledDate` " +
                "ON `recurring_transaction_occurrences` (`recurringTransactionId`, `scheduledDate`)"
        )
    }
}
