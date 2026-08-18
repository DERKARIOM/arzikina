package com.arzikina.ne.domain.model

/**
 * Cycle de vie d'une [FinancialPlan] — SAISI PAR L'UTILISATEUR (contrairement à [LoanStatus], qui
 * est toujours recalculé) : rien dans le montant disponible/prévu/restant ne détermine
 * automatiquement ce statut, l'utilisateur décide lui-même quand un projet est terminé ou archivé.
 */
enum class PlanStatus {
    /** Valeur par défaut à la création — affichée dans le bloc "Mes planifications" du Dashboard
     * (voir cahier des charges, section 18 : "résumé des planifications ACTIVES"). */
    ACTIVE,
    COMPLETED,
    ARCHIVED
}
