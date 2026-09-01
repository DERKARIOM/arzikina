package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 25 → 26 : 18ᵉ table, `user_server_links` — voir
 * [com.arzikina.ne.data.local.entity.UserServerLinkEntity] pour le détail de chaque colonne et le
 * raisonnement (étape D du chantier "audit auth + sync + doublons", login unifié). Table NEUVE
 * uniquement : contrairement à [MIGRATION_22_23], ceci ne touche PAS `UserEntity`/la table `users`
 * (la limitation Room/KSP2 documentée là-bas ne concerne que des MODIFICATIONS de `UserEntity.kt`,
 * pas l'ajout d'une table sans rapport).
 *
 * SQL reproduisant EXACTEMENT le schéma déduit de `UserServerLinkEntity` (même convention que
 * [MIGRATION_24_25]). Table créée VIDE : le rattachement se fait au premier login réussi via le
 * nouveau flux unifié, jamais rétroactivement par cette migration (aucune information serveur
 * n'est disponible pendant une migration locale hors-ligne).
 */
val MIGRATION_25_26 = object : Migration(startVersion = 25, endVersion = 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `user_server_links` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`localUserId` INTEGER NOT NULL, " +
                "`serverUserId` TEXT NOT NULL, " +
                "`linkedAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_server_links_localUserId` ON `user_server_links` (`localUserId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_server_links_serverUserId` ON `user_server_links` (`serverUserId`)")
    }
}
