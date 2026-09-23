package com.naniger.arzikina.presentation.utilities.loans

import androidx.annotation.StringRes
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.LoanType

/** Libellé de repli quand [com.naniger.arzikina.domain.model.Loan.description] est vide (voir
 * [LoanListItem.title]) : "Prêt" ou "Emprunt" selon [LoanType]. */
@StringRes
fun defaultLoanTitleRes(type: LoanType): Int =
    if (type == LoanType.LENT) R.string.loans_default_title_lent else R.string.loans_default_title_borrowed
