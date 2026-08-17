package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 16 → 17 : fonctionnalité "Lancer l'application Mobile Money depuis un compte".
 *
 * Simple `ADD COLUMN` (même pattern que [MIGRATION_15_16]/[MIGRATION_8_9], pas de recréation de
 * table) : nullable SANS `DEFAULT` — `NULL` est la valeur correcte pour tout compte déjà
 * existant (aucune application associée), pas une valeur de repli arbitraire. Voir
 * [com.arzikina.ne.domain.model.Account.mobileMoneyPackageName].
 */
val MIGRATION_16_17 = object : Migration(startVersion = 16, endVersion = 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `mobileMoneyPackageName` TEXT")
    }
}
