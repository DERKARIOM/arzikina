package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 5 → 6 : ajout de la fonctionnalité Authentification (table
 * `users`). Aucune colonne `userId` n'est encore ajoutée aux autres tables à
 * cette étape (voir la migration suivante, dédiée, qui gère aussi le
 * rattachement des données déjà existantes à un utilisateur par défaut) :
 * séparer les deux évite de mélanger "créer la table users" et "modifier 5
 * tables existantes" dans une seule migration difficile à relire et à tester.
 *
 * `COLLATE NOCASE` sur `username`/`email` et index UNIQUE correspondants :
 * voir `data/local/entity/UserEntity` pour le raisonnement (unicité et
 * recherche insensibles à la casse). Les noms d'index (`index_users_username`,
 * `index_users_email`) suivent la convention de nommage automatique de Room
 * (`index_<table>_<colonne>`) : Room valide au runtime que le schéma déclaré
 * par l'entité correspond exactement à celui produit par la migration.
 */
val MIGRATION_5_6 = object : Migration(startVersion = 5, endVersion = 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `users` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `fullName` TEXT NOT NULL,
                `username` TEXT NOT NULL COLLATE NOCASE,
                `email` TEXT NOT NULL COLLATE NOCASE,
                `phoneNumber` TEXT,
                `passwordHash` TEXT NOT NULL,
                `profilePhotoUri` TEXT,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_username` ON `users` (`username`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_email` ON `users` (`email`)")
    }
}
