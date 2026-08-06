package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 1 → 2 : ajout de la fonctionnalité Catégories (table `categories`).
 *
 * Les catégories par défaut sont insérées ici, en SQL brut, en plus du
 * peuplement fait par `RoomDatabase.Callback.onCreate` (voir `di/DatabaseModule`) :
 * `onCreate` ne se déclenche que pour une base totalement neuve. Un
 * utilisateur qui a déjà la version 1 installée passe uniquement par cette
 * migration, jamais par `onCreate` — sans cet INSERT, sa table `categories`
 * resterait vide après la mise à jour.
 */
val MIGRATION_1_2 = object : Migration(startVersion = 1, endVersion = 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `categories` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `icon` TEXT NOT NULL,
                `colorArgb` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )

        val now = System.currentTimeMillis()
        DefaultCategories.seed(now).forEach { category ->
            db.execSQL(
                """
                INSERT INTO `categories` (`name`, `icon`, `colorArgb`, `type`, `createdAt`)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(category.name, category.icon.name, category.colorArgb, category.type.name, category.createdAt)
            )
        }
    }
}
