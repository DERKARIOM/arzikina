package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 20 → 21 : nouvelle table `receipts` (voir cahier des charges "Gestion des reçus",
 * [com.arzikina.ne.data.local.entity.ReceiptEntity]) — reçus PDF centralisés, indépendants du reste
 * du modèle financier (aucune clé étrangère, voir la doc de l'entité).
 *
 * `sourceApp`/`sourceName`/`amountMinor` nullables dès la création (jamais de valeur de repli
 * inventée, voir cahier des charges section 3 : "ne pas inventer la provenance").
 */
val MIGRATION_20_21 = object : Migration(startVersion = 20, endVersion = 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `receipts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `userId` INTEGER NOT NULL,
                `fileName` TEXT NOT NULL,
                `localPath` TEXT NOT NULL,
                `receivedAt` INTEGER NOT NULL,
                `fileSize` INTEGER NOT NULL,
                `mimeType` TEXT NOT NULL,
                `sourceApp` TEXT,
                `sourceName` TEXT,
                `amountMinor` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_receipts_userId` ON `receipts` (`userId`)")
    }
}
