package com.arzikina.ne.domain.model

/**
 * Nom exact de la catégorie système générée automatiquement pour les frais supplémentaires sur
 * une transaction (voir [Transaction.feeTransactionId]) — même principe que [LoanCategoryNames],
 * dont la doc de tête détaille le raisonnement complet ("un identifiant par nom suffit, pas de
 * champ `isSystem` supplémentaire sur [Category]").
 *
 * Une seule catégorie ici (contrairement aux 4 de [LoanCategoryNames]) : les frais sont TOUJOURS
 * modélisés comme une dépense (voir `TransactionRepositoryImpl`), il n'y a pas de jambe "revenu"
 * symétrique à couvrir.
 */
object FeeCategoryNames {
    const val FEES = "Frais et commissions"

    /** Voir la doc de la classe : ensemble pratique pour un filtrage `in` (voir
     * `TransactionFormViewModel.categories`), même usage que [LoanCategoryNames.ALL]. */
    val ALL: Set<String> = setOf(FEES)
}
