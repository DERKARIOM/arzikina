package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 27 → 28 : réorganisation des comptes par glisser-déposer sur l'écran "Comptes"
 * (voir [com.arzikina.ne.domain.model.Account.displayOrder]).
 *
 * `NOT NULL DEFAULT 0` (contrairement à [MIGRATION_16_17], nullable) : `displayOrder` pilote
 * directement le tri (`ORDER BY displayOrder ASC`, voir `AccountDao.observeAllForUser`), une
 * valeur `NULL` serait ambiguë au tri SQL. Le `DEFAULT 0` satisfait uniquement la contrainte SQL
 * `NOT NULL` à l'ajout de la colonne (toutes les lignes existantes passent à `0` avant le second
 * `UPDATE` ci-dessous) — ce n'est PAS le calcul de position final.
 *
 * Rattrapage explicite de l'ordre déjà affiché AVANT cette migration (`ORDER BY createdAt ASC`,
 * l'ancien tri) : compte le nombre de comptes plus anciens du même utilisateur — équivalent d'un
 * `ROW_NUMBER() OVER (PARTITION BY userId ORDER BY createdAt, id)` sans dépendre d'une version de
 * SQLite supportant les fonctions fenêtrées (sous-requête corrélée classique, portable). Sans ce
 * rattrapage, TOUS les comptes existants partageraient `displayOrder = 0` et se retrouveraient
 * dans un ordre arbitraire (celui de la table SQLite) au premier lancement post-migration.
 *
 * Tri par (createdAt, id) — PAS createdAt seul : plusieurs comptes créés en LOT partagent
 * exactement le même `createdAt` (voir `DefaultAccounts.seed`, les 5 comptes par défaut semés à
 * l'inscription sont tous horodatés au même `now`) — sans ce départage par `id`, leur ordre
 * relatif resterait indéterminé (SQLite ne garantit aucun ordre stable pour des valeurs égales).
 * `id` (autoincrémenté par Room) reflète ici fidèlement l'ordre d'insertion réel, contrairement à
 * l'équivalent MySQL (`004_add_display_order_to_accounts.sql`) qui départage par son `id` UUID —
 * les deux calculs sont indépendants et peuvent ne pas s'accorder EXACTEMENT sur l'ordre relatif
 * d'un groupe de comptes à égalité (aucune donnée partagée ne permet de les faire coïncider) ;
 * sans conséquence pratique : le premier glisser-déposer de l'utilisateur, sur l'un ou l'autre
 * appareil, fixe un ordre réel qui, lui, se synchronise normalement.
 */
val MIGRATION_27_28 = object : Migration(startVersion = 27, endVersion = 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `displayOrder` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            UPDATE accounts
            SET displayOrder = (
                SELECT COUNT(*)
                FROM accounts AS earlier
                WHERE earlier.userId = accounts.userId
                  AND (earlier.createdAt < accounts.createdAt
                       OR (earlier.createdAt = accounts.createdAt AND earlier.id < accounts.id))
            )
            """.trimIndent()
        )
    }
}
