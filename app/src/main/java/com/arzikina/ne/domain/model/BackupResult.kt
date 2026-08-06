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
    val savingsGoalsCount: Int
)
