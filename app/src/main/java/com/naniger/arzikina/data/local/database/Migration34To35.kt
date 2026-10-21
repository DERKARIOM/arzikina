package com.naniger.arzikina.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v34 → v35 : signature manuscrite de l'émetteur de reçus — colonne nullable `signatureFileName` sur
 * `issuer_profiles` (voir [com.naniger.arzikina.data.local.entity.IssuerProfileEntity]).
 *
 * Migration purement ADDITIVE : une colonne nullable, aucun backfill. La signature copiée dans chaque
 * reçu signé vit dans le JSON de l'instantané de l'émetteur (`issuerJson`, voir `IssuedReceiptJson`) :
 * aucune colonne à ajouter sur `issued_receipts`.
 *
 * Équivalent serveur : `database/migrations/012_issuer_signature.sql`.
 */
val MIGRATION_34_35 = object : Migration(startVersion = 34, endVersion = 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `issuer_profiles` ADD COLUMN `signatureFileName` TEXT")
    }
}
