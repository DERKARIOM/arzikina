package com.arzikina.ne.domain.model

/**
 * Période optionnelle d'une [FinancialPlan] (voir cahier des charges "Planification financière",
 * section 16) — purement informative pour l'instant : aucune logique de recalcul automatique
 * (renouvellement mensuel, remise à zéro...) n'y est attachée, contrairement à [BudgetPeriod] qui
 * détermine une vraie fenêtre de calcul. Ici, [FinancialPlan.startDate]/[FinancialPlan.endDate]
 * restent de simples dates affichées, saisies librement par l'utilisateur.
 */
enum class PlanPeriodType {
    /** Aucune période définie (cas par défaut, voir cahier des charges : "la période doit rester
     * optionnelle") — [FinancialPlan.startDate]/[FinancialPlan.endDate] valent alors `null`. */
    NONE,
    MONTHLY,
    YEARLY,
    CUSTOM
}
