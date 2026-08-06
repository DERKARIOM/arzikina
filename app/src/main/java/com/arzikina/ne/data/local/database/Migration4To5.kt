package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 4 → 5 : ajout de la fonctionnalité Objectifs d'épargne (table
 * `savings_goals`). Aucune clé étrangère (voir `data/local/entity/SavingsGoalEntity`),
 * aucune donnée à peupler (comme [MIGRATION_2_3]/[MIGRATION_3_4]) : un
 * objectif n'a pas de valeur par défaut sensée.
 */
val MIGRATION_4_5 = object : Migration(startVersion = 4, endVersion = 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `savings_goals` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `targetAmount` INTEGER NOT NULL,
                `currentAmount` INTEGER NOT NULL,
                `currencyCode` TEXT NOT NULL,
                `deadline` INTEGER,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
