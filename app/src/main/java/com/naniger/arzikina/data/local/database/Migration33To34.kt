package com.naniger.arzikina.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v33 → v34 : utilitaire « Gestion de reçus » (reçus ÉMIS) — création de `issued_receipts` et
 * `issuer_profiles` (voir [com.naniger.arzikina.data.local.entity.IssuedReceiptEntity] et
 * [com.naniger.arzikina.data.local.entity.IssuerProfileEntity]).
 *
 * Migration purement ADDITIVE : deux tables neuves, aucune table existante modifiée, aucun
 * backfill (aucune donnée préexistante n'y correspond). Distinct de la table `receipts` des
 * justificatifs importés.
 *
 * Équivalent serveur : à venir avec l'étape « Synchronisation » (`database/migrations/009_…`).
 */
val MIGRATION_33_34 = object : Migration(startVersion = 33, endVersion = 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `issued_receipts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` INTEGER NOT NULL, " +
                "`number` TEXT NOT NULL, " +
                "`issuedAt` INTEGER NOT NULL, " +
                "`customerName` TEXT NOT NULL, " +
                "`customerPhone` TEXT, " +
                "`description` TEXT NOT NULL, " +
                "`amountMinor` INTEGER NOT NULL, " +
                "`currencyCode` TEXT NOT NULL, " +
                "`paymentMethod` TEXT NOT NULL, " +
                "`transactionReference` TEXT, " +
                "`notes` TEXT, " +
                "`issuedByName` TEXT, " +
                "`issuedByTitle` TEXT, " +
                "`pricingJson` TEXT, " +
                "`issuerJson` TEXT NOT NULL, " +
                "`pdfFileName` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`syncId` TEXT, " +
                "`deletedAt` INTEGER, " +
                "`version` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_issued_receipts_userId` ON `issued_receipts` (`userId`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_issued_receipts_userId_number` " +
                "ON `issued_receipts` (`userId`, `number`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_issued_receipts_userId_issuedAt` " +
                "ON `issued_receipts` (`userId`, `issuedAt`)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_issued_receipts_syncId` ON `issued_receipts` (`syncId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `issuer_profiles` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` INTEGER NOT NULL, " +
                "`businessName` TEXT NOT NULL, " +
                "`address` TEXT NOT NULL, " +
                "`phone` TEXT NOT NULL, " +
                "`email` TEXT, " +
                "`taxId` TEXT, " +
                "`logoFileName` TEXT, " +
                "`defaultIssuedByName` TEXT, " +
                "`defaultIssuedByTitle` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`syncId` TEXT, " +
                "`deletedAt` INTEGER, " +
                "`version` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_issuer_profiles_userId` ON `issuer_profiles` (`userId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_issuer_profiles_syncId` ON `issuer_profiles` (`syncId`)")
    }
}
