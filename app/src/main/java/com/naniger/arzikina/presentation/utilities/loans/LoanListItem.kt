package com.naniger.arzikina.presentation.utilities.loans

import com.naniger.arzikina.domain.model.LoanStatus
import com.naniger.arzikina.domain.model.LoanType

/**
 * Un [com.naniger.arzikina.domain.model.Loan] enrichi des informations nécessaires à son affichage
 * (nom de la personne, devise du compte associé) — voir [LoansViewModel], qui les résout en
 * combinant [com.naniger.arzikina.domain.repository.PersonRepository]/[com.naniger.arzikina.domain.repository.AccountRepository].
 * Montants en unité mineure (voir `Loan.amount`) : le formatage (séparateur de milliers, symbole
 * de devise) reste la responsabilité de la couche d'affichage (voir `LoansAdapter`), comme pour
 * [com.naniger.arzikina.presentation.accounts.AccountUiItem].
 */
data class LoanListItem(
    val id: Long,
    val personName: String,
    val type: LoanType,
    val status: LoanStatus,
    /** [com.naniger.arzikina.domain.model.Loan.description] BRUT, potentiellement vide — le libellé de
     * repli générique ("Prêt"/"Emprunt", voir [defaultLoanTitleRes]) est appliqué au moment de
     * l'affichage (voir `LoansAdapter`), pas ici : il dépend d'une ressource de chaîne localisée,
     * que cette classe (pure, sans dépendance Android) n'a pas à connaître. */
    val title: String,
    val amountMinor: Long,
    val amountRepaidMinor: Long,
    val remainingAmountMinor: Long,
    val currencyCode: String,
    /** 0..100, voir [computeLoanProgressPercent]. */
    val progressPercent: Int
)
