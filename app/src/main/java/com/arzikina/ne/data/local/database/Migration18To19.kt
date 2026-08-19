package com.arzikina.ne.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 18 → 19 : période fixe (dates de début/fin) pour la fonctionnalité Budget, en plus du
 * mode récurrent existant (semaine/mois civil, voir [com.arzikina.ne.domain.model.BudgetPeriod]) —
 * voir cahier des charges "Amélioration de la fonctionnalité Budget — Gestion d'une période".
 *
 * Deux changements, tous deux sur la table `budgets` existante (aucune nouvelle table) :
 * - Ajout de `startDate`/`endDate` (`INTEGER`, nullable, défaut `NULL`) : `NULL` pour tout budget
 *   existant avant cette migration (mode récurrent legacy, inchangé, voir
 *   [com.arzikina.ne.domain.model.Budget]) ; renseignés uniquement pour les nouveaux budgets à
 *   période fixe créés après cette migration.
 * - `index_budgets_categoryId` : passe de `UNIQUE` à simple (recréation de l'index, même nom que
 *   celui généré automatiquement par Room pour rester cohérent avec le schéma déclaré dans
 *   [BudgetEntity], voir [MIGRATION_5_6] pour la convention de nommage). Nécessaire pour permettre
 *   plusieurs budgets successifs sur une même catégorie (un par période) ; la règle "un seul budget
 *   ACTIF par catégorie à la fois" reste appliquée, mais uniquement côté présentation.
 *
 * Pas de backfill : les colonnes ajoutées restent `NULL` pour toutes les lignes existantes, ce qui
 * les préserve exactement dans leur mode récurrent actuel (aucune donnée perdue ni modifiée).
 */
val MIGRATION_18_19 = object : Migration(startVersion = 18, endVersion = 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `budgets` ADD COLUMN `startDate` INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE `budgets` ADD COLUMN `endDate` INTEGER DEFAULT NULL")

        db.execSQL("DROP INDEX IF EXISTS `index_budgets_categoryId`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_categoryId` ON `budgets` (`categoryId`)")
    }
}
