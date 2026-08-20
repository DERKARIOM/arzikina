package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 21 → 22 : lien optionnel entre une transaction et le reçu PDF dont elle est issue
 * (cahier des charges "Créer une transaction depuis un reçu", voir
 * [com.arzikina.ne.domain.model.Transaction.receiptId]).
 *
 * Simple `ADD COLUMN` (même pattern que [MIGRATION_16_17]) : nullable SANS `DEFAULT` — `NULL` est
 * la valeur correcte pour toute transaction déjà existante (jamais issue d'un reçu), pas une
 * valeur de repli arbitraire.
 *
 * Volontairement SANS `ForeignKey` vers `receipts` (même raisonnement que `feeTransactionId`, voir
 * la doc de tête de [com.arzikina.ne.data.local.entity.TransactionEntity]) : une FK exigerait de
 * recréer entièrement la table `transactions` (SQLite ne sait pas ajouter de FK par `ALTER TABLE`,
 * voir [MIGRATION_9_10]). La cohérence (référence effacée quand le reçu associé est supprimé) est
 * garantie au niveau applicatif par `ReceiptRepositoryImpl.deleteReceipt`, dans le même esprit que
 * `TransactionDao.clearFeeTransactionReference`.
 *
 * Index sur `receiptId` : recherche fréquente en sens inverse (un reçu donné a-t-il déjà une
 * transaction associée ? voir `TransactionDao.findByReceiptId`, utilisé pour l'anti-doublon du
 * bouton "Ajouter comme transaction").
 */
val MIGRATION_21_22 = object : Migration(startVersion = 21, endVersion = 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `receiptId` INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_receiptId` ON `transactions` (`receiptId`)")
    }
}
