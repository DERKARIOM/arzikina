package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 1 → 2 : ajout de la fonctionnalité Catégories (table `categories`).
 *
 * Les catégories par défaut sont insérées ici, en SQL brut, pour qu'un
 * utilisateur qui avait déjà la version 1 installée avant l'introduction des
 * catégories en profite aussi après la mise à jour (sans cet INSERT, sa
 * table `categories` resterait vide).
 *
 * `DefaultCategories.seed(now, userId = 0L)` : le `0L` est un identifiant
 * factice SANS SIGNIFICATION ici — à cette version historique du schéma, la
 * colonne `userId` n'existe pas encore sur `categories` (elle n'arrive qu'à
 * la version 7, voir [MIGRATION_6_7]) et cet `INSERT` n'en écrit d'ailleurs
 * aucune. Seuls `name`/`icon`/`colorArgb`/`type`/`createdAt` sont
 * effectivement lus sur chaque [com.arzikina.ne.data.local.entity.CategoryEntity]
 * temporaire retourné par `seed()`.
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
        DefaultCategories.seed(now, userId = 0L).forEach { category ->
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
