package com.naniger.arzikina.presentation.utilities.recurring

import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.Category
import com.naniger.arzikina.domain.model.OccurrenceStatus
import com.naniger.arzikina.domain.model.RecurringTransaction

/**
 * Une ligne affichable de l'écran "Transactions planifiées" (voir [RecurringTransactionsViewModel]) :
 * soit une VRAIE [com.naniger.arzikina.domain.model.RecurringTransactionOccurrence] déjà en base
 * ("À traiter"/"Historique", voir [occurrenceId]/[status] non nuls), soit une échéance PROJETÉE à
 * partir de [RecurringTransaction.nextExecutionDate] ("À venir", voir [occurrenceId]/[status] nuls) —
 * cette dernière ne deviendra une vraie occurrence qu'au prochain appel de
 * `RecurringTransactionRepository.generateMissingOccurrences`, voir sa doc.
 *
 * @param account/[category] résolus par le ViewModel (voir `TransactionUiItem` pour le même
 * principe) : `null` si supprimés entre-temps, l'affichage doit rester robuste dans ce cas.
 */
data class RecurringOccurrenceUiItem(
    val occurrenceId: Long?,
    val recurringTransaction: RecurringTransaction,
    val account: Account?,
    val category: Category?,
    val scheduledDate: Long,
    val status: OccurrenceStatus?,
    val processedAt: Long? = null
)
