package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arzikina.ne.domain.model.SecurityQuestion

/**
 * Migration 7 → 8 : question de sécurité pour la réinitialisation locale du
 * mot de passe (voir `domain/model/SecurityQuestion` et l'écran "Mot de
 * passe oublié").
 *
 * `securityAnswerHash` par défaut `''` (chaîne vide) : [PasswordHasher] la
 * traite comme un hachage mal formé et [PasswordHasher.verify] retourne
 * alors systématiquement `false`, jamais une exception — un compte créé
 * avant cette migration (l'utilisateur "par défaut" créé par [MIGRATION_6_7])
 * ne peut donc PAS encore utiliser "Mot de passe oublié" tant qu'il n'a pas
 * défini une vraie question/réponse depuis l'écran Profil (étape ultérieure
 * de la feuille de route Authentification) — comportement sûr par défaut
 * (refuse plutôt que d'accepter n'importe quelle réponse), pas une régression
 * puisque ce mécanisme n'existait pas du tout avant cette version.
 */
val MIGRATION_7_8 = object : Migration(startVersion = 7, endVersion = 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `users` ADD COLUMN `securityQuestion` TEXT NOT NULL " +
                "DEFAULT '${SecurityQuestion.FIRST_PET_NAME.name}'"
        )
        db.execSQL("ALTER TABLE `users` ADD COLUMN `securityAnswerHash` TEXT NOT NULL DEFAULT ''")
    }
}
