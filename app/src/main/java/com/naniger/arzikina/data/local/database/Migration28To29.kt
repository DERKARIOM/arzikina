package com.naniger.arzikina.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 28 → 29 : fonctionnalité "Marketplace personnelle" (modèles de transaction
 * réutilisables, voir [com.naniger.arzikina.data.local.entity.TransactionTemplateEntity]).
 *
 * Nouvelle table dont le SQL reproduit EXACTEMENT le schéma déduit de l'entité (même convention
 * que [MIGRATION_13_14]) : `FOREIGN KEY ... ON DELETE CASCADE` vers `accounts` (même principe que
 * `recurring_transactions.accountId`), sans contrainte vers `categories` — mais `categoryId` ici
 * `NOT NULL` (jamais de transfert pour un modèle, voir la doc du domaine).
 *
 * Aucun backfill nécessaire : aucune donnée préexistante ne correspond à un modèle de transaction.
 */
val MIGRATION_28_29 = object : Migration(startVersion = 28, endVersion = 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `transaction_templates` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`amount` INTEGER NOT NULL, " +
                "`categoryId` INTEGER NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`description` TEXT NOT NULL, " +
                "`isFavorite` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`syncId` TEXT, " +
                "`deletedAt` INTEGER, " +
                "`version` INTEGER NOT NULL, " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_templates_accountId` ON `transaction_templates` (`accountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_templates_categoryId` ON `transaction_templates` (`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_templates_userId` ON `transaction_templates` (`userId`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_transaction_templates_syncId` " +
                "ON `transaction_templates` (`syncId`)"
        )
    }
}
