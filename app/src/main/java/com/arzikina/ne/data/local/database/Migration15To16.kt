package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 15 → 16 : fonctionnalité "Gestion des frais supplémentaires sur les transactions".
 *
 * Simple `ADD COLUMN` (même pattern que [MIGRATION_8_9], pas de recréation de table) : les deux
 * nouvelles colonnes sont nullables SANS `DEFAULT` — comme `transactions.paymentMethod`
 * ([MIGRATION_8_9]) — car `NULL` est la valeur correcte pour toute transaction déjà existante
 * (aucune n'a de frais), pas une valeur de repli arbitraire.
 *
 * Volontairement PAS de `FOREIGN KEY` sur `feeTransactionId` (lien vers une autre ligne de la
 * MÊME table `transactions`) : voir la doc de tête de `TransactionEntity` pour le raisonnement
 * (ajouter une FK à une table existante exigerait de la recréer entièrement, comme
 * [MIGRATION_9_10] ; la cohérence de ce lien est garantie par `TransactionRepositoryImpl`).
 */
val MIGRATION_15_16 = object : Migration(startVersion = 15, endVersion = 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `feeTransactionId` INTEGER")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `feeType` TEXT")
    }
}
