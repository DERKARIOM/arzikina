package com.arzikina.ne.presentation.utilities.financialplan

import com.arzikina.ne.domain.model.FinancialPlanItem

/**
 * Une ligne de la liste de l'écran "Détail de la planification" (voir [FinancialPlanDetailAdapter]) :
 * un [Header] (résumé, une seule fois) suivi d'un [ItemRow] par dépense prévue — même principe que
 * `LoanDetailListRow` (voir sa doc).
 */
sealed interface FinancialPlanDetailListRow {
    data class Header(val uiState: FinancialPlanDetailUiState) : FinancialPlanDetailListRow
    data class ItemRow(val item: FinancialPlanItem) : FinancialPlanDetailListRow
}
