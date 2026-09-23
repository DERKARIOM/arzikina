package com.naniger.arzikina.presentation.utilities.marketplace

import androidx.annotation.StringRes

/**
 * Ligne d'un seul RecyclerView pour l'écran "Marketplace personnelle" (voir [MarketplaceAdapter]) —
 * même principe qu'un seul RecyclerView qui défile en bloc que `LoansListRow` (voir instructions
 * projet, "optimise les listes longues").
 */
sealed interface MarketplaceListRow {

    /**
     * Sépare "⭐ Mes favoris" du reste (cahier des charges section 7) — affiché UNIQUEMENT en
     * l'absence de recherche/filtre par catégorie actif (voir [MarketplaceViewModel.uiState]) : une
     * liste déjà filtrée par l'utilisateur n'a pas besoin d'être re-sectionnée, un simple résultat
     * plat reste plus lisible dans ce cas.
     */
    data class SectionHeader(@StringRes val titleRes: Int) : MarketplaceListRow

    data class TemplateRow(val item: TransactionTemplateListItem) : MarketplaceListRow

    /** Recherche/filtre actif ne retournant aucun modèle — voir `LoansListRow.NoResults`, même
     * raisonnement (distinct de l'état vide "aucun modèle du tout", voir [MarketplaceFragment]). */
    data object NoResults : MarketplaceListRow
}
