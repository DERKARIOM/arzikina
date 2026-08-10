package com.arzikina.ne.presentation.utilities.loans

import com.arzikina.ne.domain.model.LoanPayment
import com.arzikina.ne.domain.model.LoanType

/**
 * Une ligne de la liste de l'écran "Détail du prêt/emprunt" (voir [LoanDetailAdapter]) — même
 * principe que [LoansListRow] sur l'écran principal : un unique RecyclerView plutôt qu'un
 * en-tête statique + une liste imbriquée.
 */
sealed interface LoanDetailListRow {
    data class Header(val uiState: LoanDetailUiState) : LoanDetailListRow

    /** [loanType] : voir la doc de [LoanDetailAdapter] pour son usage (signe +/- et couleur du
     * montant, un remboursement étant reçu si LENT, versé si BORROWED). [currencyCode] : celle du
     * compte du PRÊT (voir [LoanDetailUiState.currencyCode]), pas du compte de règlement propre à
     * ce versement — même simplification volontaire que partout ailleurs dans Arzikina (aucune
     * conversion de change, voir la doc de [com.arzikina.ne.domain.model.Loan]). */
    data class PaymentRow(
        val payment: LoanPayment,
        val accountName: String,
        val loanType: LoanType,
        val currencyCode: String
    ) : LoanDetailListRow
}
