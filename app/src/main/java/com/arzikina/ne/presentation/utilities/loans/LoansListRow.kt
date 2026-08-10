package com.arzikina.ne.presentation.utilities.loans

/**
 * Ligne d'un seul RecyclerView pour l'écran principal Prêts/Emprunts (voir [LoansAdapter]) : les
 * cartes de résumé + l'en-tête de liste forment la PREMIÈRE ligne ([Header]), suivie d'une ligne
 * [LoanRow] par prêt/emprunt. Une seule RecyclerView qui défile en bloc plutôt qu'un
 * RecyclerView imbriqué dans un conteneur scrollable (voir instructions projet, "optimise les
 * listes longues") — et plus simple que synchroniser deux zones de défilement séparées.
 */
sealed interface LoansListRow {
    data class Header(val summary: LoansSummary) : LoansListRow
    data class LoanRow(val item: LoanListItem) : LoansListRow

    /**
     * Recherche/filtre actif ne retournant aucun prêt/emprunt — ligne affichée à la place des
     * [LoanRow] (jamais en même temps qu'elles), TOUJOURS précédée du [Header] : les totaux
     * "Total reçu"/"Total dû" doivent rester visibles même quand le filtre courant ne retourne
     * rien (voir la doc de [LoansUiState.summary], "vue d'ensemble stable").
     */
    data object NoResults : LoansListRow
}
