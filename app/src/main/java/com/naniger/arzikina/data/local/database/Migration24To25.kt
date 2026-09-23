package com.naniger.arzikina.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 24 → 25 : 17ᵉ table, `user_preferences` — voir
 * [com.naniger.arzikina.data.local.entity.UserPreferencesEntity] pour le détail de chaque colonne et le
 * raisonnement (étape 22 du chantier de synchronisation multi-appareils, `themeMode`/`currencyCode`
 * migrés depuis DataStore Preferences vers Room pour réutiliser telle quelle la mécanique du Sync
 * Engine, voir docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md, section 6.5).
 *
 * SQL reproduisant EXACTEMENT le schéma déduit de `UserPreferencesEntity` (même convention de
 * nommage d'index automatique `index_<table>_<colonne(s)>` que le reste du projet, voir
 * [MIGRATION_12_13]). Table créée VIDE : aucune ligne n'est peuplée ici depuis DataStore — voir
 * `UserPreferencesRepositoryImpl.observePreferences`, qui migre paresseusement (au premier accès)
 * les valeurs déjà présentes en DataStore pour un utilisateur existant, plutôt qu'un `INSERT`
 * bulk ici qui n'aurait aucun moyen fiable d'associer chaque valeur DataStore (globale à
 * l'appareil, jamais scopée par `userId`) à l'utilisateur COURANT au moment de cette migration.
 */
val MIGRATION_24_25 = object : Migration(startVersion = 24, endVersion = 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `user_preferences` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` INTEGER NOT NULL, " +
                "`themeMode` TEXT NOT NULL, " +
                "`currencyCode` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`syncId` TEXT, " +
                "`updatedAt` INTEGER NOT NULL DEFAULT 0, " +
                "`deletedAt` INTEGER, " +
                "`version` INTEGER NOT NULL DEFAULT 1)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_preferences_userId` ON `user_preferences` (`userId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_preferences_syncId` ON `user_preferences` (`syncId`)")
    }
}
