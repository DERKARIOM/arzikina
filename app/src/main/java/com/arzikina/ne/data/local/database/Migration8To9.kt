package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 8 → 9 : moyen de paiement optionnel sur une transaction (voir
 * `domain/model/PaymentMethod`).
 *
 * Colonne nullable SANS `DEFAULT` (contrairement à [MIGRATION_7_8], dont les
 * deux colonnes sont `NOT NULL`) : toutes les transactions existantes
 * reçoivent `NULL`, ce qui correspond exactement à leur état réel — "moyen de
 * paiement non précisé" — plutôt qu'une valeur arbitraire comme [PaymentMethod.OTHER].
 */
val MIGRATION_8_9 = object : Migration(startVersion = 8, endVersion = 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `paymentMethod` TEXT")
    }
}
