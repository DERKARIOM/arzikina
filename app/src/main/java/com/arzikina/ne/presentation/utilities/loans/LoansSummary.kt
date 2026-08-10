package com.arzikina.ne.presentation.utilities.loans

import com.arzikina.ne.domain.model.CurrencyAmount

/**
 * Cartes de résumé de l'écran principal Prêts/Emprunts (voir `item_loan_summary_header.xml`).
 *
 * [totalReceivable]/[totalOwed] sont des LISTES de [CurrencyAmount] (une entrée par devise
 * utilisée), pas un simple [Long] : voir la doc de [CurrencyAmount] — les prêts/emprunts peuvent
 * porter sur des comptes de devises différentes, et Arzikina ne fait aucune conversion de change.
 * Additionner des montants de devises différentes en un seul nombre produirait un chiffre erroné.
 *
 * Interprétation retenue pour "Total reçu"/"Total dû" (maquette non ambiguë sur ce point précis) :
 * le solde RESTANT à recevoir/payer (somme de [com.arzikina.ne.domain.model.Loan.remainingAmount]),
 * pas le montant original des prêts/emprunts — cohérent avec le reste de la maquette, qui met
 * partout en avant le "Restant" plutôt que le montant initial (carte de prêt individuelle, écran
 * de détail).
 */
data class LoansSummary(
    val totalReceivable: List<CurrencyAmount>,
    val lentCount: Int,
    val totalOwed: List<CurrencyAmount>,
    val borrowedCount: Int,
    val totalCount: Int
)
