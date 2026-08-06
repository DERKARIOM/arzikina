package com.arzikina.ne.presentation.budget

import com.arzikina.ne.domain.model.Budget
import com.arzikina.ne.domain.model.Category

/**
 * Projection d'affichage combinant un [Budget] avec sa [Category] et le
 * montant déjà dépensé sur la période en cours. Construite par
 * [BudgetViewModel] : le domaine ne stocke jamais de progression, seulement
 * la règle de plafond (voir [Budget]).
 *
 * [spentMinor] ne compte que les dépenses dans la devise du budget (voir
 * [Budget.currencyCode]) — pas de conversion de change dans l'application.
 * [progress] peut dépasser `1f` en cas de dépassement du plafond.
 */
data class BudgetUiItem(
    val budget: Budget,
    val category: Category?,
    val spentMinor: Long,
    val progress: Float
)
