package com.arzikina.ne.data.local.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 12 → 13 : fonctionnalité Prêts / Emprunts.
 *
 * Trois nouvelles tables, dont le SQL reproduit EXACTEMENT le schéma que Room déduit des entités
 * `PersonEntity`/`LoanEntity`/`LoanPaymentEntity` (mêmes noms d'index que la convention automatique
 * `index_<table>_<colonne>`, voir [MIGRATION_5_6]) :
 * - `persons` : les personnes avec qui l'utilisateur prête/emprunte. Pas de `FOREIGN KEY` vers
 *   `users` (même limite que les autres tables, voir [MIGRATION_6_7]).
 * - `loans` : un prêt accordé ou un emprunt reçu, avec `FOREIGN KEY ... ON DELETE CASCADE` vers
 *   `persons` et `accounts` (ces deux tables existent déjà avec un vrai support des clés
 *   étrangères depuis leur création — pas la même limite que `userId`). `transactionId` : ajouté
 *   pendant l'étape DAO/Repository (voir `domain/model/Loan.transactionId`) — un prêt/emprunt
 *   génère toujours une transaction de décaissement, ce champ permet de la retrouver/supprimer
 *   proprement. Cette migration n'ayant encore jamais été exécutée sur un appareil réel au moment
 *   de cet ajout, elle est corrigée directement plutôt que d'introduire une migration 13→14 pour
 *   une seule colonne.
 * - `loan_payments` : historique des remboursements, `FOREIGN KEY ... ON DELETE CASCADE` vers
 *   `loans` et `accounts`. Pas de contrainte vers `transactions` (voir `domain/model/LoanPayment`).
 *
 * Backfill des 4 catégories par défaut Prêts/Emprunts (voir [DefaultCategories]) pour CHAQUE
 * utilisateur déjà existant au moment de cette migration : contrairement à [MIGRATION_1_2] (une
 * seule ligne factice `userId = 0L`, base mono-utilisateur de l'époque), on itère ici sur la
 * table `users` réelle, puisque l'authentification multi-utilisateurs existe depuis la version 7.
 */
val MIGRATION_12_13 = object : Migration(startVersion = 12, endVersion = 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `persons` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`phone` TEXT, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_persons_userId` ON `persons` (`userId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `loans` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` INTEGER NOT NULL, " +
                "`personId` INTEGER NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`amount` INTEGER NOT NULL, " +
                "`amountRepaid` INTEGER NOT NULL, " +
                "`remainingAmount` INTEGER NOT NULL, " +
                "`startDate` INTEGER NOT NULL, " +
                "`dueDate` INTEGER NOT NULL, " +
                "`reason` TEXT NOT NULL, " +
                "`reasonCustomText` TEXT, " +
                "`repaymentMode` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`transactionId` INTEGER NOT NULL, " +
                "FOREIGN KEY(`personId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_personId` ON `loans` (`personId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_accountId` ON `loans` (`accountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_userId` ON `loans` (`userId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `loan_payments` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` INTEGER NOT NULL, " +
                "`loanId` INTEGER NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`amount` INTEGER NOT NULL, " +
                "`date` INTEGER NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`transactionId` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`loanId`) REFERENCES `loans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_payments_loanId` ON `loan_payments` (`loanId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_payments_accountId` ON `loan_payments` (`accountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_payments_userId` ON `loan_payments` (`userId`)")

        backfillLoanCategories(db)
    }

    private fun backfillLoanCategories(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        val userIds = mutableListOf<Long>()
        db.query("SELECT `id` FROM `users`").use { cursor ->
            while (cursor.moveToNext()) {
                userIds.add(cursor.getLong(0))
            }
        }

        userIds.forEach { userId ->
            DefaultCategories.seed(now, userId)
                .filter { it.icon.name == "LOAN" }
                .forEach { category ->
                    db.insert(
                        "categories",
                        SQLiteDatabase.CONFLICT_ABORT,
                        ContentValues().apply {
                            put("userId", category.userId)
                            put("name", category.name)
                            put("icon", category.icon.name)
                            put("colorArgb", category.colorArgb)
                            put("type", category.type.name)
                            put("createdAt", category.createdAt)
                        }
                    )
                }
        }
    }
}
