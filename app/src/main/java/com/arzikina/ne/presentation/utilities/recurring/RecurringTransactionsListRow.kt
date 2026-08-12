package com.arzikina.ne.presentation.utilities.recurring

import androidx.annotation.StringRes

/**
 * Ligne d'une seule RecyclerView pour l'écran "Transactions planifiées" — même principe que
 * `LoansListRow` (cartes de résumé + sections qui défilent en bloc, voir sa doc pour le
 * raisonnement, "optimise les listes longues").
 *
 * Les trois sections ([RecurringSection]) sont générées par [RecurringTransactionsViewModel] :
 * une section VIDE n'apparaît PAS du tout dans la liste (pas de [SectionTitle] ni de ligne vide
 * associée). L'absence TOTALE de règle récurrente (aucune donnée dans aucune des 3 sections) n'est
 * PAS représentée ici : voir `RecurringTransactionsFragment.emptyState`, une vue séparée qui
 * remplace alors la RecyclerView entière — même principe que `fragment_loans.xml`.
 */
sealed interface RecurringTransactionsListRow {
    data class Header(val summary: RecurringTransactionsSummary) : RecurringTransactionsListRow

    data class SectionTitle(@StringRes val titleRes: Int, val count: Int) : RecurringTransactionsListRow

    data class OccurrenceRow(val item: RecurringOccurrenceUiItem, val section: RecurringSection) : RecurringTransactionsListRow
}

/** À quelle section appartient une [RecurringOccurrenceUiItem] (voir sa doc) — contrôle la mise en
 * forme de sa ligne dans `RecurringOccurrenceItemBinder` (sous-titre compte/date pour À
 * traiter/À venir, statut/date pour Historique). */
enum class RecurringSection {
    PENDING, UPCOMING, HISTORY
}
