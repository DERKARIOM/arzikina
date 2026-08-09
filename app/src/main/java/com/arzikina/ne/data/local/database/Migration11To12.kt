package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 11 → 12 : table `card_secrets` (numéro complet + CVV chiffrés d'une carte de crédit,
 * voir `data/local/entity/CardSecretEntity`/`data/security/CardCipher`).
 *
 * Nouvelle table (pas de colonnes ajoutées sur `accounts`, voir la doc de [CardSecretEntity] pour
 * le raisonnement de cette séparation) : simple `CREATE TABLE`, rien à copier depuis une table
 * existante. Le SQL doit reproduire EXACTEMENT le schéma que Room déduit de `@Entity`/`@ForeignKey`
 * (aucun index supplémentaire : `accountId` étant la clé primaire, SQLite l'indexe déjà
 * implicitement — en ajouter un explicitement ferait échouer la validation de schéma de Room au
 * démarrage suivant).
 */
val MIGRATION_11_12 = object : Migration(startVersion = 11, endVersion = 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `card_secrets` (" +
                "`accountId` INTEGER NOT NULL, " +
                "`cardNumberEncrypted` TEXT NOT NULL, " +
                "`cardNumberIv` TEXT NOT NULL, " +
                "`cardCvvEncrypted` TEXT NOT NULL, " +
                "`cardCvvIv` TEXT NOT NULL, " +
                "PRIMARY KEY(`accountId`), " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
    }
}
