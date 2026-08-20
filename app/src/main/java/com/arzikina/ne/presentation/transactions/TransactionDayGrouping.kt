package com.arzikina.ne.presentation.transactions

import com.arzikina.ne.util.DatePeriods
import com.arzikina.ne.util.DayLabel
import java.time.LocalDate

/**
 * Regroupement des transactions par jour — voir [DayLabel] (déplacé dans `util/`, partagé avec
 * "Gestion des reçus") pour le type de libellé lui-même. Partagé entre l'écran Transactions
 * ([TransactionsViewModel]) et "Détail du compte"
 * ([com.arzikina.ne.presentation.accounts.AccountDetailViewModel]) : un seul endroit pour cette
 * logique, plutôt que de la dupliquer entre les deux (elle vivait initialement uniquement dans
 * AccountDetailViewModel).
 */
data class TransactionDaySection(
    val label: DayLabel,
    val items: List<TransactionUiItem>
)

/**
 * Regroupe par jour, du plus récent au plus ancien. Suppose [this] déjà trié
 * du plus récent au plus ancien (c'est le cas de tout ce qui descend de
 * [TransactionRepository.observeTransactions], `ORDER BY date DESC`) : chaque
 * section conserve cet ordre pour ses transactions.
 */
fun List<TransactionUiItem>.groupByDay(): List<TransactionDaySection> {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    return groupBy { DatePeriods.toLocalDate(it.transaction.date) }
        .toSortedMap(compareByDescending { it })
        .map { (date, dayItems) ->
            TransactionDaySection(
                label = when (date) {
                    today -> DayLabel.Today
                    yesterday -> DayLabel.Yesterday
                    else -> DayLabel.Other(date)
                },
                items = dayItems
            )
        }
}
