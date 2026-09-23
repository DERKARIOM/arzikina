package com.naniger.arzikina.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 22 → 23 : première étape — purement locale — du chantier de synchronisation
 * multi-appareils (voir `docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md`, sections 8 et 10).
 *
 * Ajoute 4 colonnes de synchronisation à 13 des 15 tables existantes :
 * - `syncId` (TEXT, nullable, index UNIQUE) : UUID partagé Android/API/MySQL, additif (option B,
 *   section 6.3 du document) — généré par le futur Sync Engine au premier envoi d'une ligne, PAS à
 *   sa création locale ; l'`id` Room auto-incrémenté reste la clé primaire, inchangée. SQLite
 *   autorise plusieurs `NULL` dans un index `UNIQUE` (une ligne jamais synchronisée n'entre donc
 *   jamais en conflit avec une autre).
 * - `updatedAt` (INTEGER) : horodatage de dernière modification, base de la détection de conflit
 *   (Last-Write-Wins, section 9). Cinq tables l'ont déjà (`loans`, `recurring_transactions`,
 *   `financial_plans`, `financial_plan_items`, `receipts`) — inchangées ici, réutilisées telles
 *   quelles. Pour les 9 autres, colonne ajoutée avec un rattrapage explicite : `updatedAt =
 *   createdAt` pour toute ligne déjà existante (une ligne jamais modifiée depuis sa création a, par
 *   définition, `updatedAt == createdAt` — même principe de rattrapage que [MIGRATION_6_7] pour
 *   `userId`).
 * - `deletedAt` (INTEGER, nullable) : suppression douce (section 8). `NULL` = ligne active ; AUCUNE
 *   ligne existante n'est marquée supprimée par cette migration. La suppression reste un `DELETE`
 *   SQL immédiat tant que le Sync Engine n'est pas branché dans les repositories — étape
 *   ultérieure, non faite ici.
 * - `version` (INTEGER NOT NULL DEFAULT 1) : compteur de version optimiste pour la détection de
 *   conflit côté serveur (section 9).
 *
 * `card_secrets` (voir [com.naniger.arzikina.data.local.entity.CardSecretEntity]) est volontairement
 * ABSENTE de cette migration : décision validée section 6.2 du document ci-dessus (secret chiffré
 * par une clé Android Keystore non exportable, non transportable vers un autre appareil/serveur
 * tel quel — reste 100% local, comme aujourd'hui).
 *
 * `users` (voir [com.naniger.arzikina.data.local.entity.UserEntity]) est ÉGALEMENT absente de cette
 * migration, pour une raison différente : limitation constatée du toolchain Room/KSP2 utilisé par
 * ce projet (voir `docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md`, section 6.5). En bref, toute
 * modification de `UserEntity.kt` — même sans rapport avec la nature du changement — fait échouer
 * `kspDebugKotlin` avec une erreur `[MissingType]`, très probablement liée à l'usage de
 * `@ColumnInfo(collate = ColumnInfo.NOCASE)` sur `username`/`email` (seule entité du projet à
 * utiliser cet attribut) combiné à la génération incrémentale de KSP2. `UserEntity` reste donc,
 * pour l'instant, strictement identique à sa forme d'avant ce chantier — traitement similaire à
 * `card_secrets` ci-dessus, mais motivé par une contrainte d'outillage et non par un choix
 * architectural définitif. À revisiter lors d'une montée de version Room/KSP, ou en retirant
 * `collate = NOCASE` au profit d'une autre stratégie d'insensibilité à la casse (ex. normalisation
 * en minuscules à l'écriture).
 *
 * Étape volontairement SANS effet visible pour l'utilisateur : aucun repository n'écrit encore
 * dans ces nouvelles colonnes (viendra avec le Sync Engine, étape ultérieure), aucune table
 * `sync_queue` n'existe encore à ce stade. Uniquement le schéma, pour permettre au reste du
 * chantier d'avancer par petites étapes vérifiables (méthode de travail habituelle du projet).
 */
val MIGRATION_22_23 = object : Migration(startVersion = 22, endVersion = 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Tables sans `updatedAt` existant : colonne ajoutée + rattrapage depuis `createdAt`.
        addSyncColumns(db, table = "accounts", addUpdatedAt = true)
        addSyncColumns(db, table = "categories", addUpdatedAt = true)
        addSyncColumns(db, table = "transactions", addUpdatedAt = true)
        addSyncColumns(db, table = "budgets", addUpdatedAt = true)
        addSyncColumns(db, table = "savings_goals", addUpdatedAt = true)
        // `users` : volontairement absente (voir doc de tête — limitation KSP2/collate, distincte
        // de l'exclusion de `card_secrets`).
        addSyncColumns(db, table = "persons", addUpdatedAt = true)
        addSyncColumns(db, table = "loan_payments", addUpdatedAt = true)
        addSyncColumns(db, table = "recurring_transaction_occurrences", addUpdatedAt = true)

        // Tables ayant déjà `updatedAt` (voir doc de tête) : pas de deuxième colonne, réutilisée telle quelle.
        addSyncColumns(db, table = "loans", addUpdatedAt = false)
        addSyncColumns(db, table = "recurring_transactions", addUpdatedAt = false)
        addSyncColumns(db, table = "financial_plans", addUpdatedAt = false)
        addSyncColumns(db, table = "financial_plan_items", addUpdatedAt = false)
        addSyncColumns(db, table = "receipts", addUpdatedAt = false)

        // `card_secrets` : volontairement absente de cette liste (voir doc de tête, décision 6.2).
    }

    private fun addSyncColumns(
        db: SupportSQLiteDatabase,
        table: String,
        addUpdatedAt: Boolean
    ) {
        db.execSQL("ALTER TABLE `$table` ADD COLUMN `syncId` TEXT")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_syncId` ON `$table` (`syncId`)")
        db.execSQL("ALTER TABLE `$table` ADD COLUMN `deletedAt` INTEGER")
        db.execSQL("ALTER TABLE `$table` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
        if (addUpdatedAt) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE `$table` SET `updatedAt` = `createdAt` WHERE `updatedAt` = 0")
        }
    }
}
