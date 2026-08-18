package com.arzikina.ne.domain.model

/**
 * Icônes disponibles pour une [FinancialPlan] (voir cahier des charges "Planification financière",
 * section 7 : "💰 Dépenses du mois", "💍 Mariage", "🏍️ Achat d'une moto", "✈️ Voyage",
 * "🏠 Construction"...).
 *
 * Le domaine ne dépend jamais d'Android : le mapping vers un `@DrawableRes` concret se fait
 * uniquement dans la couche presentation (voir `AccountIcon`/`AccountIconDisplay` pour le même
 * principe déjà établi).
 */
enum class FinancialPlanIcon {
    WALLET,
    RING,
    MOTORCYCLE,
    PLANE,
    HOME,
    GRADUATION,
    CELEBRATION,
    OTHER
}
