package com.arzikina.ne.presentation.utilities.recurring

/**
 * Cartes de résumé de l'écran "Transactions planifiées" (voir `item_recurring_summary_header.xml`).
 * [totalCount] = [pendingCount] + [upcomingCount] (voir maquette de référence, "Total" = somme des
 * deux) — n'inclut PAS l'historique, qui n'est pas une échéance active.
 */
data class RecurringTransactionsSummary(
    val pendingCount: Int,
    val upcomingCount: Int
) {
    val totalCount: Int get() = pendingCount + upcomingCount
}
