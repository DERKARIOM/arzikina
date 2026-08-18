package com.arzikina.ne.domain.model

/**
 * État d'une [FinancialPlanItem] (voir cahier des charges "Planification financière", section 10).
 *
 * SAISI/CHANGÉ PAR L'UTILISATEUR — [TO_PLAN] à la création, [DONE] après conversion en transaction
 * réelle (voir la doc de [FinancialPlanItem.transactionId]) ou marquage manuel, [CANCELLED] si la
 * dépense prévue ne sera finalement pas effectuée. Jamais recalculé automatiquement.
 */
enum class PlanItemStatus {
    /** "À prévoir" — valeur par défaut, la dépense n'a pas encore été effectuée. */
    TO_PLAN,
    /** "Réalisée" — voir [FinancialPlanItem.actualAmount], renseigné (ou non) au même moment. */
    DONE,
    /** "Annulée" — la dépense ne sera finalement pas effectuée, reste visible dans l'historique
     * plutôt que d'être supprimée (voir cahier des charges, section 10). */
    CANCELLED
}
