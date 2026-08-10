package com.arzikina.ne.domain.model

/**
 * Noms exacts des 4 catégories système générées automatiquement pour la fonctionnalité
 * Prêts/Emprunts (voir [Loan]) — vivent dans le domaine (pas dans `data/local/database/DefaultCategories`,
 * qui les référence désormais d'ici) car deux couches indépendantes en ont besoin :
 * - la couche data (`LoanRepositoryImpl`, `DefaultCategories`) pour les créer/retrouver ;
 * - la couche présentation (`TransactionFormViewModel`) pour les exclure du sélecteur de
 *   catégorie manuel (voir sa doc, section "Synchronisation avec les transactions") — une
 *   dépendance directe de la présentation vers `data` casserait la Clean Architecture, alors que
 *   les deux couches peuvent dépendre du domaine sans problème.
 *
 * Un simple ensemble de noms plutôt qu'un champ `isSystem`/`isHidden` sur [Category] (changement
 * de schéma Room) : ces 4 catégories sont connues et fixes, un identifiant par nom suffit sans
 * migration ni colonne supplémentaire — cohérent avec "évite les colonnes inutiles" (instructions
 * projet).
 */
object LoanCategoryNames {
    const val DISBURSEMENT_LENT = "Prêt accordé"
    const val REPAYMENT_LENT = "Remboursement de prêt reçu"
    const val DISBURSEMENT_BORROWED = "Emprunt reçu"
    const val REPAYMENT_BORROWED = "Remboursement d'emprunt"

    /** Voir la doc de la classe : ensemble pratique pour un filtrage `in` (voir
     * `TransactionFormViewModel.categories`). */
    val ALL: Set<String> = setOf(DISBURSEMENT_LENT, REPAYMENT_LENT, DISBURSEMENT_BORROWED, REPAYMENT_BORROWED)
}
