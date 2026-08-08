package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 9 → 10 : transfert d'argent entre deux comptes (voir
 * `domain/model/TransactionType.TRANSFER`).
 *
 * Contrairement à [MIGRATION_8_9] (simple `ADD COLUMN`), cette migration doit
 * RECRÉER la table `transactions` : `categoryId` était `NOT NULL` depuis
 * l'origine (voir [MIGRATION_2_3]) et un transfert n'a pas de catégorie (voir
 * `TransactionType`), ce qui exige de retirer cette contrainte — SQLite ne
 * sait pas modifier la nullabilité d'une colonne existante avec `ALTER TABLE`,
 * seulement en ajouter une. On suit donc le protocole standard SQLite/Room de
 * recréation de table :
 * 1. Créer `transactions_new` avec le schéma cible (`categoryId` nullable,
 *    nouvelle colonne `transferAccountId` nullable avec sa propre clé
 *    étrangère vers `accounts`, en CASCADE comme `accountId`).
 * 2. Copier toutes les lignes existantes (`transferAccountId` = NULL partout :
 *    aucune transaction existante n'est un transfert).
 * 3. Supprimer l'ancienne table et renommer la nouvelle.
 * 4. Recréer les index (mêmes noms que ceux que Room générerait lui-même,
 *    pour que la validation de schéma au démarrage suivant passe).
 *
 * `PRAGMA foreign_keys=OFF/ON` autour de l'opération : requis par SQLite pour
 * une recréation de table impliquant des clés étrangères (éviter que la
 * suppression de l'ancienne table déclenche une vérification de contrainte
 * intermédiaire invalide).
 */
val MIGRATION_9_10 = object : Migration(startVersion = 9, endVersion = 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=OFF")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `transactions_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` INTEGER NOT NULL, " +
                "`amount` INTEGER NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`transferAccountId` INTEGER, " +
                "`categoryId` INTEGER, " +
                "`date` INTEGER NOT NULL, " +
                "`description` TEXT NOT NULL, " +
                "`receiptPhotoUri` TEXT, " +
                "`latitude` REAL, " +
                "`longitude` REAL, " +
                "`paymentMethod` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`transferAccountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)"
        )

        db.execSQL(
            "INSERT INTO `transactions_new` (" +
                "`id`, `userId`, `amount`, `type`, `accountId`, `transferAccountId`, `categoryId`, " +
                "`date`, `description`, `receiptPhotoUri`, `latitude`, `longitude`, `paymentMethod`, `createdAt`) " +
                "SELECT `id`, `userId`, `amount`, `type`, `accountId`, NULL, `categoryId`, " +
                "`date`, `description`, `receiptPhotoUri`, `latitude`, `longitude`, `paymentMethod`, `createdAt` " +
                "FROM `transactions`"
        )

        db.execSQL("DROP TABLE `transactions`")
        db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_transferAccountId` ON `transactions` (`transferAccountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_userId` ON `transactions` (`userId`)")

        db.execSQL("PRAGMA foreign_keys=ON")
    }
}
