package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 14 → 15 : fonctionnalité "Exclure un compte des statistiques".
 *
 * Simple `ADD COLUMN` (même pattern que [MIGRATION_10_11]) : `isExcludedFromStatistics`
 * `NOT NULL DEFAULT 0` — Room stocke un `Boolean` en `INTEGER` nativement, sans
 * `TypeConverter` (même représentation que `recurring_transactions.isActive`, voir
 * [MIGRATION_13_14]). `DEFAULT 0` garantit que chaque compte déjà existant reste inclus
 * dans les statistiques personnelles exactement comme avant cette fonctionnalité —
 * aucune donnée existante n'est affectée.
 *
 * Aucun `UPDATE` nécessaire après l'`ALTER TABLE` (contrairement à [MIGRATION_10_11]) :
 * il n'existe aucune règle de dérivation à appliquer, `false` est la valeur correcte
 * pour tous les comptes préexistants, pas seulement une valeur de repli temporaire.
 */
val MIGRATION_14_15 = object : Migration(startVersion = 14, endVersion = 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `accounts` ADD COLUMN `isExcludedFromStatistics` INTEGER NOT NULL DEFAULT 0"
        )
    }
}
