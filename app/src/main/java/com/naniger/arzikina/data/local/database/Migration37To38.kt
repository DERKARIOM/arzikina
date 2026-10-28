package com.naniger.arzikina.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v37 → v38 : mode d'utilisation choisi à l'inscription — colonne NULLABLE `usageMode` sur `users`
 * (voir [com.naniger.arzikina.domain.model.UsageMode] et [com.naniger.arzikina.data.local.entity.UserEntity]).
 *
 * Migration purement ADDITIVE, sans backfill : `NULL` signifie « jamais demandé » pour les utilisateurs déjà
 * inscrits, qui conservent leurs comptes, soldes et transactions tels quels et ne sont jamais invités à
 * s'inscrire de nouveau. Aucune donnée n'est lue, modifiée ni supprimée.
 */
val MIGRATION_37_38 = object : Migration(startVersion = 37, endVersion = 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `users` ADD COLUMN `usageMode` TEXT")
    }
}
