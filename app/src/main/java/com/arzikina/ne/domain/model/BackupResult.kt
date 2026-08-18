package com.arzikina.ne.domain.model

/**
 * Bilan chiffré d'un export ou d'une restauration, utilisé par la couche
 * presentation pour informer l'utilisateur de ce qui a été traité (ex.
 * "12 comptes, 340 transactions restaurés") sans lui montrer de détails
 * techniques.
 */
data class BackupResult(
    val accountsCount: Int,
    val categoriesCount: Int,
    val transactionsCount: Int,
    val budgetsCount: Int,
    val savingsGoalsCount: Int,
    val loansCount: Int = 0,
    /** Voir `RecurringTransactionDto` — nombre de RÈGLES récurrentes (pas leurs occurrences,
     * voir [occurrencesCount] séparément), ajouté en même temps que leur prise en charge par la
     * sauvegarde. */
    val recurringTransactionsCount: Int = 0,
    val occurrencesCount: Int = 0,
    /** Voir `FinancialPlanDto`/`FinancialPlanItemDto` — nombre de PLANIFICATIONS (pas leurs dépenses
     * prévues, voir [planItemsCount] séparément), ajouté en même temps que leur prise en charge par
     * la sauvegarde (Étape 10). */
    val plansCount: Int = 0,
    val planItemsCount: Int = 0
)
