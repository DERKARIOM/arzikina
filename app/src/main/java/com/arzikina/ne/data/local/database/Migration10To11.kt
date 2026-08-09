package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 10 → 11 : compte de type Carte de crédit (voir
 * `domain/model/AccountType`).
 *
 * Simple `ADD COLUMN` (comme [MIGRATION_8_9], pas de recréation de table) :
 * les 4 nouvelles colonnes sont soit nullables sans `DEFAULT` (`cardLastFourDigits`,
 * `cardExpiryMonth`, `cardExpiryYear` — sans objet pour un compte classique,
 * donc `NULL` est la valeur correcte, pas une valeur arbitraire), soit
 * `NOT NULL DEFAULT 'CASH'` pour `type` afin de respecter la contrainte Room
 * (l'enum [com.arzikina.ne.domain.model.AccountType] n'est volontairement PAS
 * nullable côté domaine).
 *
 * `'CASH'` n'est qu'une valeur de repli temporaire : l'`UPDATE` qui suit
 * corrige immédiatement `type` pour chaque compte existant à partir de son
 * `icon` actuel (BANK/MOBILE_MONEY/SAVINGS → type correspondant, le reste →
 * CASH) — c'est CETTE valeur dérivée qui doit survivre, pas le défaut brut.
 * Sans cet `UPDATE`, un compte "Banque" existant se retrouverait reclassé
 * `CASH`, ce qui casserait la promesse "les comptes existants restent
 * fonctionnels exactement comme avant" (rien dans le comportement actuel de
 * l'app ne dépend encore de `type`, mais un futur écran qui filtrerait par
 * type verrait sinon un historique incohérent).
 */
val MIGRATION_10_11 = object : Migration(startVersion = 10, endVersion = 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'CASH'")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `cardLastFourDigits` TEXT")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `cardExpiryMonth` INTEGER")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `cardExpiryYear` INTEGER")

        db.execSQL(
            "UPDATE `accounts` SET `type` = CASE `icon` " +
                "WHEN 'BANK' THEN 'BANK' " +
                "WHEN 'MOBILE_MONEY' THEN 'MOBILE_MONEY' " +
                "WHEN 'SAVINGS' THEN 'SAVINGS' " +
                "ELSE 'CASH' END"
        )
    }
}
