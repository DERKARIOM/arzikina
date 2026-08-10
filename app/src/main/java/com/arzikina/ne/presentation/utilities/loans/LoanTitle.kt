package com.arzikina.ne.presentation.utilities.loans

import androidx.annotation.StringRes
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.LoanType

/** Libellé de repli quand [com.arzikina.ne.domain.model.Loan.description] est vide (voir
 * [LoanListItem.title]) : "Prêt" ou "Emprunt" selon [LoanType]. */
@StringRes
fun defaultLoanTitleRes(type: LoanType): Int =
    if (type == LoanType.LENT) R.string.loans_default_title_lent else R.string.loans_default_title_borrowed
