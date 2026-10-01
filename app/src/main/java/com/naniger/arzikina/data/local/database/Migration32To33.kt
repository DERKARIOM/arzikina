package com.naniger.arzikina.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v32 → v33 : « Transformer un prêt/emprunt en cadeau » (voir `Loan.giftedAmount`,
 * `Loan.giftTransactionId`, `Loan.giftedAt`, `LoanStatus.GIFTED`).
 *
 * Trois colonnes ADDITIVES sur `loans`, aucune donnée existante modifiée :
 * - `giftedAmount` `NOT NULL DEFAULT 0` : 0 = jamais transformé, ce qui est exactement l'état de
 *   tous les prêts/emprunts existants (même convention que `accounts.displayOrder`, voir
 *   [MIGRATION_27_28]) ;
 * - `giftTransactionId`/`giftedAt` nullables, sans clé étrangère (même raisonnement que
 *   `loans.transactionId`).
 *
 * `transactions` n'est PAS modifiée : la traçabilité « transformé depuis un prêt/emprunt » est
 * portée par le prêt (`giftTransactionId`), jamais par la transaction. Le nouveau statut `GIFTED`
 * est une simple valeur TEXT de plus pour `loans.status` : aucune contrainte à adapter.
 *
 * Équivalent serveur : `database/migrations/008_loan_gift.sql`.
 */
val MIGRATION_32_33 = object : Migration(startVersion = 32, endVersion = 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `loans` ADD COLUMN `giftedAmount` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `loans` ADD COLUMN `giftTransactionId` INTEGER")
        db.execSQL("ALTER TABLE `loans` ADD COLUMN `giftedAt` INTEGER")
    }
}
