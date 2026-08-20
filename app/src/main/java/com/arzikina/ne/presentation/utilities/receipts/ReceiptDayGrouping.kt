package com.arzikina.ne.presentation.utilities.receipts

import com.arzikina.ne.domain.model.Receipt
import com.arzikina.ne.util.DatePeriods
import com.arzikina.ne.util.DayLabel
import java.time.LocalDate

/**
 * Regroupement des reçus par jour — cahier des charges "Gestion des reçus", section 4 : "groupé par
 * date (Aujourd'hui/Hier/date), trié du plus récent au plus ancien". Même principe que
 * `presentation/transactions/TransactionDayGrouping.kt` (voir [DayLabel], déplacé dans `util/`
 * précisément pour être partagé entre les deux) — une fonction distincte ici plutôt qu'une
 * réutilisation directe de `List<TransactionUiItem>.groupByDay()` : celle-ci est fortement couplée à
 * `TransactionUiItem` (compte/catégorie résolus), un type sans rapport avec [Receipt].
 *
 * Groupe sur [Receipt.receivedAt] (date de RÉCEPTION dans Arzikina, voir sa doc) — jamais
 * [Receipt.createdAt]/[Receipt.updatedAt], qui ne reflètent pas la même notion pour l'utilisateur
 * (voir cahier des charges section 4, "reçus reçus depuis d'autres applications").
 */
data class ReceiptDaySection(
    val label: DayLabel,
    val items: List<Receipt>
)

/**
 * Regroupe par jour, du plus récent au plus ancien. Suppose [this] déjà trié du plus récent au plus
 * ancien (c'est le cas de tout ce qui descend de [com.arzikina.ne.domain.repository.ReceiptRepository.observeReceipts],
 * `ORDER BY receivedAt DESC`) : chaque section conserve cet ordre pour ses reçus.
 */
fun List<Receipt>.groupByDay(): List<ReceiptDaySection> {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    return groupBy { DatePeriods.toLocalDate(it.receivedAt) }
        .toSortedMap(compareByDescending { it })
        .map { (date, dayItems) ->
            ReceiptDaySection(
                label = when (date) {
                    today -> DayLabel.Today
                    yesterday -> DayLabel.Yesterday
                    else -> DayLabel.Other(date)
                },
                items = dayItems
            )
        }
}
