package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 17 → 18 : fonctionnalité "Planification" (planification financière par projet — voir
 * cahier des charges "Nouvelle fonctionnalité : Planification financière"). INDÉPENDANTE de
 * "Automatisation" (ex-"Planification", transactions récurrentes, tables `recurring_transactions`/
 * `recurring_transaction_occurrences` créées par [MIGRATION_13_14]) : aucune table partagée,
 * aucune relation entre les deux.
 *
 * Deux nouvelles tables, SQL reproduisant EXACTEMENT le schéma déduit de
 * `FinancialPlanEntity`/`FinancialPlanItemEntity` (mêmes noms d'index que la convention
 * automatique `index_<table>_<colonne>`, voir [MIGRATION_5_6]) :
 * - `financial_plans` : AUCUNE `FOREIGN KEY` (contrairement à `loans`) — une planification n'est
 *   reliée à aucun compte ni aucune autre table, voir `FinancialPlanEntity`.
 * - `financial_plan_items` : `FOREIGN KEY ... ON DELETE CASCADE` vers `financial_plans` (supprimer
 *   une planification supprime ses dépenses prévues), et `FOREIGN KEY` (`NO_ACTION`, comme
 *   `transactions.categoryId`) vers `categories` pour la catégorie optionnelle.
 *
 * Pas de backfill nécessaire : contrairement à [MIGRATION_12_13] (catégories système Prêts/
 * Emprunts), cette fonctionnalité ne dépend d'aucune catégorie par défaut.
 */
val MIGRATION_17_18 = object : Migration(startVersion = 17, endVersion = 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `financial_plans` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`description` TEXT, " +
                "`availableAmount` INTEGER NOT NULL, " +
                "`targetAmount` INTEGER, " +
                "`periodType` TEXT NOT NULL, " +
                "`startDate` INTEGER, " +
                "`endDate` INTEGER, " +
                "`icon` TEXT NOT NULL, " +
                "`colorArgb` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_plans_userId` ON `financial_plans` (`userId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `financial_plan_items` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` INTEGER NOT NULL, " +
                "`planId` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`amount` INTEGER NOT NULL, " +
                "`actualAmount` INTEGER, " +
                "`categoryId` INTEGER, " +
                "`description` TEXT, " +
                "`plannedDate` INTEGER, " +
                "`priority` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`transactionId` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`planId`) REFERENCES `financial_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_plan_items_planId` ON `financial_plan_items` (`planId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_plan_items_categoryId` ON `financial_plan_items` (`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_plan_items_userId` ON `financial_plan_items` (`userId`)")
    }
}
