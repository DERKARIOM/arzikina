package com.naniger.arzikina.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v31 → v32 : « Créer un modèle à partir d'une transaction » (voir
 * `TransactionTemplate.sourceTransactionId`).
 *
 * Une seule colonne NULLABLE, sans défaut ni clé étrangère : `NULL` pour tous les modèles existants
 * (aucun n'a été créé depuis une transaction). La table `transactions` n'est PAS touchée — la
 * relation est portée par le modèle, jamais par la transaction. Équivalent serveur :
 * `database/migrations/007_transaction_template_source.sql`.
 */
val MIGRATION_31_32 = object : Migration(startVersion = 31, endVersion = 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transaction_templates` ADD COLUMN `sourceTransactionId` INTEGER")
    }
}
