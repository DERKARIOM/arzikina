package com.arzikina.ne.domain.model

/**
 * Cadence de renouvellement d'un [Budget]. La fenêtre de calcul de la
 * progression (mois civil en cours ou semaine ISO en cours) est déterminée
 * par la couche presentation au moment de l'affichage, jamais stockée.
 */
enum class BudgetPeriod {
    WEEKLY,
    MONTHLY
}
