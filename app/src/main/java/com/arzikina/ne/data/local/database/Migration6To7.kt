package com.arzikina.ne.data.local.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arzikina.ne.util.Constants
import com.arzikina.ne.util.PasswordHasher

/**
 * Migration 6 → 7 : isolation des données par utilisateur.
 *
 * Ajoute une colonne `userId` (voir les entités dans `data/local/entity`) aux 5 tables
 * existantes (comptes, catégories, transactions, budgets, objectifs
 * d'épargne). Toute installation antérieure à l'authentification a des
 * données SANS propriétaire : cette migration crée un utilisateur "par
 * défaut" (voir [Constants.LEGACY_DEFAULT_USER_USERNAME]) et lui rattache
 * automatiquement tout ce qui existe déjà, plutôt que de perdre ces données
 * (décision validée avec l'équipe produit).
 *
 * Technique : `ALTER TABLE ... ADD COLUMN userId INTEGER NOT NULL DEFAULT <id>`
 * avec l'identifiant RÉEL de l'utilisateur par défaut comme valeur littérale
 * du `DEFAULT` — SQLite applique alors ce défaut à TOUTES les lignes
 * existantes en une seule opération, sans `UPDATE` séparé.
 *
 * Limite connue et acceptée pour l'instant : SQLite ne permet pas d'ajouter
 * une contrainte `FOREIGN KEY` à une table déjà existante sans la recréer
 * entièrement (ce que ferait `ALTER TABLE` classique). Cette migration se
 * contente donc d'un index simple par table (performances de requête),
 * SANS contrainte de clé étrangère réelle vers `users` — voir les KDoc des
 * entités concernées. L'intégrité réelle est assurée par le code
 * applicatif (chaque repository ne lit/écrit que via [com.arzikina.ne.domain.repository.SessionManager]).
 * Une contrainte `FOREIGN KEY ... ON DELETE CASCADE` complète pourra être
 * ajoutée plus tard, via une migration dédiée recréant ces tables, LE JOUR
 * où une fonctionnalité de suppression de compte utilisateur existera
 * réellement (elle n'existe pas encore).
 */
val MIGRATION_6_7 = object : Migration(startVersion = 6, endVersion = 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val defaultUserId = db.insert(
            "users",
            SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("fullName", Constants.LEGACY_DEFAULT_USER_FULL_NAME)
                put("username", Constants.LEGACY_DEFAULT_USER_USERNAME)
                put("email", Constants.LEGACY_DEFAULT_USER_EMAIL)
                putNull("phoneNumber")
                put("passwordHash", PasswordHasher.hash(Constants.LEGACY_DEFAULT_USER_PASSWORD))
                putNull("profilePhotoUri")
                put("createdAt", System.currentTimeMillis())
            }
        )

        addUserIdColumn(db, table = "accounts", defaultUserId = defaultUserId)
        addUserIdColumn(db, table = "categories", defaultUserId = defaultUserId)
        addUserIdColumn(db, table = "transactions", defaultUserId = defaultUserId)
        addUserIdColumn(db, table = "budgets", defaultUserId = defaultUserId)
        addUserIdColumn(db, table = "savings_goals", defaultUserId = defaultUserId)
    }

    private fun addUserIdColumn(db: SupportSQLiteDatabase, table: String, defaultUserId: Long) {
        db.execSQL("ALTER TABLE `$table` ADD COLUMN `userId` INTEGER NOT NULL DEFAULT $defaultUserId")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_${table}_userId` ON `$table` (`userId`)")
    }
}
