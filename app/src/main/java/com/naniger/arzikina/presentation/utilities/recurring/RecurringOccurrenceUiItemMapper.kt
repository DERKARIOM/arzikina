package com.naniger.arzikina.presentation.utilities.recurring

import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.Category
import com.naniger.arzikina.domain.model.RecurringTransaction
import com.naniger.arzikina.domain.model.RecurringTransactionOccurrence

/**
 * Résout la règle/le compte/la catégorie d'une occurrence déjà en base ("À traiter"/"Historique"/
 * dialogue d'édition) — partagé entre [RecurringTransactionsViewModel] et
 * [RecurringOccurrenceEditViewModel] pour ne jamais dupliquer cette logique de jointure (extrait de
 * [RecurringTransactionsViewModel], seul endroit où elle vivait jusqu'à l'ajout du dialogue
 * d'édition).
 *
 * `null` si sa règle d'origine a disparu entre-temps (ne devrait pas arriver,
 * `RecurringTransactionEntity` est en `CASCADE` sur ses occurrences, mais une lecture ne doit
 * jamais supposer un état impossible).
 */
fun RecurringTransactionOccurrence.toUiItem(
    rulesById: Map<Long, RecurringTransaction>,
    accountsById: Map<Long, Account>,
    categoriesById: Map<Long, Category>
): RecurringOccurrenceUiItem? {
    val rule = rulesById[recurringTransactionId] ?: return null
    return RecurringOccurrenceUiItem(
        occurrenceId = id,
        recurringTransaction = rule,
        account = accountsById[rule.accountId],
        category = rule.categoryId?.let { categoriesById[it] },
        scheduledDate = scheduledDate,
        status = status,
        processedAt = processedAt
    )
}
