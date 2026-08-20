package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 19 → 20 : heure de déclenchement (`triggerHour`/`triggerMinute`) pour une automatisation
 * (voir cahier des charges "Ajouter l'heure de déclenchement à Automatisation") — seule la table
 * `recurring_transactions` change, `recurring_transaction_occurrences` n'est pas concernée.
 *
 * Deux colonnes `INTEGER NOT NULL DEFAULT 8`/`DEFAULT 0` (08:00) : toute règle créée avant cette
 * version reçoit cette valeur de repli sans qu'aucune ligne existante ne soit perdue ou modifiée
 * autrement (voir [com.arzikina.ne.domain.model.RecurringTransaction.DEFAULT_TRIGGER_HOUR]/
 * [com.arzikina.ne.domain.model.RecurringTransaction.DEFAULT_TRIGGER_MINUTE], mêmes valeurs des deux
 * côtés). `NOT NULL` avec défaut plutôt que nullable : voir la doc de `RecurringTransactionEntity`,
 * ce choix évite de complexifier chaque calcul/programmation en aval avec un `triggerHour` absent.
 *
 * Pas de reprogrammation d'alarme ici : cette migration ne fait que mettre à jour le schéma, la
 * (re)programmation réelle des automatisations actives est de la responsabilité d'`AutomationScheduler`
 * (voir sa doc), appelé au démarrage de l'application comme `RecurringOccurrencesScheduler`.
 */
val MIGRATION_19_20 = object : Migration(startVersion = 19, endVersion = 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `recurring_transactions` ADD COLUMN `triggerHour` INTEGER NOT NULL DEFAULT 8")
        db.execSQL("ALTER TABLE `recurring_transactions` ADD COLUMN `triggerMinute` INTEGER NOT NULL DEFAULT 0")
    }
}
