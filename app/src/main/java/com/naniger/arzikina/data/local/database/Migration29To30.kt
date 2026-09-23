package com.naniger.arzikina.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 29 → 30 : "Heure par défaut" pour un modèle de transaction (cahier des charges
 * "Marketplace personnelle", extension — voir
 * [com.naniger.arzikina.data.local.entity.TransactionTemplateEntity.defaultHour]/[defaultMinute]).
 *
 * Deux colonnes NULLABLE ajoutées à `transaction_templates` (même principe additif que
 * [MIGRATION_19_20] pour `recurring_transactions.triggerHour`/`triggerMinute`) — `NULL` par défaut
 * pour toutes les lignes déjà existantes, ce qui correspond exactement à "pas d'heure par défaut" :
 * aucun backfill nécessaire, comportement inchangé pour les modèles déjà créés.
 */
val MIGRATION_29_30 = object : Migration(startVersion = 29, endVersion = 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transaction_templates` ADD COLUMN `defaultHour` INTEGER")
        db.execSQL("ALTER TABLE `transaction_templates` ADD COLUMN `defaultMinute` INTEGER")
    }
}
