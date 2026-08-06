package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 2 → 3 : ajout de la fonctionnalité Transactions (table
 * `transactions`), avec ses clés étrangères vers `accounts` et `categories`
 * et leurs index (voir `data/local/entity/TransactionEntity` pour le
 * raisonnement complet sur les contraintes `ON DELETE`).
 *
 * Aucune donnée à peupler ici : contrairement aux comptes et catégories, une
 * transaction n'a pas de valeur par défaut sensée ; la table démarre vide
 * aussi bien pour une nouvelle installation que pour une mise à jour.
 */
val MIGRATION_2_3 = object : Migration(startVersion = 2, endVersion = 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `transactions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `amount` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `accountId` INTEGER NOT NULL,
                `categoryId` INTEGER NOT NULL,
                `date` INTEGER NOT NULL,
                `description` TEXT NOT NULL,
                `receiptPhotoUri` TEXT,
                `latitude` REAL,
                `longitude` REAL,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON DELETE NO ACTION
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)")
    }
}
