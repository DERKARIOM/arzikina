package com.naniger.arzikina.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 23 → 24 : deuxième étape — toujours purement locale — du chantier de synchronisation
 * multi-appareils (voir docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md, section 8). Après la migration
 * 22→23 (colonnes de sync sur les entités existantes), celle-ci ajoute la 16ᵉ table : `sync_queue`,
 * la file d'attente locale des écritures en attente d'envoi au serveur (voir
 * [com.naniger.arzikina.data.local.entity.SyncQueueEntity] pour le détail de chaque colonne).
 *
 * SQL reproduisant EXACTEMENT le schéma déduit de `SyncQueueEntity` (même convention de nommage
 * d'index automatique `index_<table>_<colonne(s)>` que le reste du projet, voir [MIGRATION_12_13]).
 *
 * Étape volontairement SANS effet visible pour l'utilisateur : table créée mais VIDE et
 * inutilisée — aucun repository n'y écrit encore, aucun Sync Engine ne la lit encore (viendra dans
 * une étape ultérieure, une fois le client réseau et le stockage sécurisé du token en place).
 */
val MIGRATION_23_24 = object : Migration(startVersion = 23, endVersion = 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_queue` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`entityType` TEXT NOT NULL, " +
                "`entitySyncId` TEXT NOT NULL, " +
                "`operation` TEXT NOT NULL, " +
                "`payloadJson` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`retryCount` INTEGER NOT NULL DEFAULT 0, " +
                "`lastAttemptAt` INTEGER, " +
                "`status` TEXT NOT NULL, " +
                "`errorMessage` TEXT)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_queue_status` ON `sync_queue` (`status`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sync_queue_entityType_entitySyncId` " +
                "ON `sync_queue` (`entityType`, `entitySyncId`)"
        )
    }
}
