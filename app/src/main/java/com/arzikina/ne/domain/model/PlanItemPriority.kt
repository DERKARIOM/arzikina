package com.arzikina.ne.domain.model

/**
 * Priorité d'une [FinancialPlanItem] (voir cahier des charges "Planification financière",
 * section 9) — saisie librement par l'utilisateur, jamais recalculée ni utilisée dans
 * [FinancialPlanItem.amount]/le calcul du montant restant : purement indicative, pour aider
 * l'utilisateur à arbitrer visuellement en cas de dépassement (section 15).
 */
enum class PlanItemPriority {
    ESSENTIAL,
    IMPORTANT,
    OPTIONAL
}
