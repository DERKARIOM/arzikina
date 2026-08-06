package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 3 → 4 : ajout de la fonctionnalité Budgets (table `budgets`),
 * avec sa clé étrangère vers `categories` et son index unique (voir
 * `data/local/entity/BudgetEntity` pour le raisonnement complet).
 *
 * Aucune donnée à peupler ici, comme pour [MIGRATION_2_3] : un budget n'a pas
 * de valeur par défaut sensée, la table démarre vide.
 */
val MIGRATION_3_4 = object : Migration(startVersion = 3, endVersion = 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `budgets` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `categoryId` INTEGER NOT NULL,
                `period` TEXT NOT NULL,
                `limitAmount` INTEGER NOT NULL,
                `currencyCode` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_categoryId` ON `budgets` (`categoryId`)")
    }
}
