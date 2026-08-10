package com.arzikina.ne.presentation.utilities.loans

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.LoanStatus
import com.arzikina.ne.domain.model.LoanType

/**
 * Libellé + couleur de la pastille de statut d'un prêt/emprunt (voir `item_loan.xml`).
 *
 * [LoanStatus.REPAID] est toujours vert (succès), quel que soit [LoanType] — voir maquette,
 * "Emprunt urgence" (BORROWED, remboursé) affiche la même pastille verte que "Prêt études"
 * (LENT, remboursé). [LoanStatus.ONGOING] en revanche change de couleur SELON [LoanType] : vert
 * si l'argent doit REVENIR vers l'utilisateur (LENT), rouge si l'argent doit en SORTIR
 * (BORROWED) — même logique que les catégories par défaut de cette fonctionnalité (voir
 * `data/local/database/DefaultCategories`).
 *
 * [LoanStatus.OVERDUE]/[LoanStatus.UPCOMING] ne figurent pas sur la maquette fournie : couleurs
 * choisies par cohérence (rouge = urgence pour un retard, neutre pour une échéance future), à
 * ajuster si une maquette dédiée les précise plus tard.
 */
data class LoanStatusDisplay(
    @StringRes val labelRes: Int,
    @ColorRes val colorRes: Int
)

fun loanStatusDisplay(status: LoanStatus, type: LoanType): LoanStatusDisplay = when (status) {
    LoanStatus.REPAID -> LoanStatusDisplay(R.string.loans_status_repaid, R.color.loan_lent_color)
    LoanStatus.ONGOING -> if (type == LoanType.LENT) {
        LoanStatusDisplay(R.string.loans_status_ongoing, R.color.loan_lent_color)
    } else {
        LoanStatusDisplay(R.string.loans_status_ongoing, R.color.expense_red)
    }
    LoanStatus.OVERDUE -> LoanStatusDisplay(R.string.loans_status_overdue, R.color.expense_red)
    LoanStatus.UPCOMING -> LoanStatusDisplay(R.string.loans_status_upcoming, R.color.arzikina_outline)
}
