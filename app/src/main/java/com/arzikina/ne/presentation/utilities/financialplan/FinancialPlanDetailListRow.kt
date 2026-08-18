package com.arzikina.ne.presentation.utilities.financialplan

import com.arzikina.ne.domain.model.FinancialPlanItem

/**
 * Une ligne de la liste de l'écran "Détail de la planification" (voir [FinancialPlanDetailAdapter]) :
 * un [Header] (résumé, une seule fois) suivi d'un [ItemRow] par dépense prévue — même principe que
 * `LoanDetailListRow` (voir sa doc).
 *
 * [ItemRow.isFirst]/[ItemRow.isLast] : position de la dépense dans la liste (calculée par
 * `FinancialPlanDetailFragment.render`), utilisée par [FinancialPlanDetailAdapter] pour choisir le
 * fond "postcard" (coins arrondis en haut/bas/les deux/aucun) qui fait apparaître toutes les
 * dépenses comme un seul bloc à couleur uniforme — même principe que
 * `TransactionListRow.Row.isLastInSection`.
 */
sealed interface FinancialPlanDetailListRow {
    data class Header(val uiState: FinancialPlanDetailUiState) : FinancialPlanDetailListRow
    data class ItemRow(
        val item: FinancialPlanItem,
        val isFirst: Boolean,
        val isLast: Boolean
    ) : FinancialPlanDetailListRow
}
