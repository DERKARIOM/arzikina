package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 26 → 27 : 19ᵉ table, `user_profile_photos` — voir
 * [com.arzikina.ne.data.local.entity.UserProfilePhotoEntity] pour le détail de chaque colonne et
 * le raisonnement (cahier des charges "Gestion de la photo de profil"). Table NEUVE uniquement :
 * comme [MIGRATION_25_26], ceci ne touche PAS `UserEntity`/la table `users` (la limitation
 * Room/KSP2 documentée dans [MIGRATION_22_23] ne concerne que des MODIFICATIONS de
 * `UserEntity.kt`, pas l'ajout d'une table sans rapport).
 *
 * SQL reproduisant EXACTEMENT le schéma déduit de `UserProfilePhotoEntity` (même convention que
 * [MIGRATION_25_26]). Table créée VIDE : chaque utilisateur existant n'aura de ligne qu'au premier
 * changement de photo (jamais rétroactivement par cette migration — aucune photo n'existe avant
 * cette fonctionnalité).
 */
val MIGRATION_26_27 = object : Migration(startVersion = 26, endVersion = 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `user_profile_photos` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` INTEGER NOT NULL, " +
                "`localPath` TEXT, " +
                "`serverUrl` TEXT, " +
                "`version` INTEGER NOT NULL, " +
                "`pendingUpload` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_profile_photos_userId` ON `user_profile_photos` (`userId`)")
    }
}
