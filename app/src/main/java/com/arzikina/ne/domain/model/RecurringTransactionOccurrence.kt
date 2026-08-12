package com.arzikina.ne.domain.model

/**
 * L'exécution réelle d'une [RecurringTransaction] à une date donnée (voir sa doc pour le
 * raisonnement de cette séparation règle/occurrence).
 *
 * @param scheduledDate date d'échéance de CETTE occurrence (jour calendaire) — indépendante d'une
 * éventuelle modification ponctuelle du montant/compte/catégorie/description au moment de
 * l'enregistrement (voir [status] [OccurrenceStatus.MODIFIED]), qui ne change ni [scheduledDate] ni
 * la règle d'origine.
 * @param status voir [OccurrenceStatus] — TOUJOURS écrit explicitement par
 * `RecurringTransactionRepository` au moment où l'utilisateur agit, jamais recalculé à la lecture.
 * @param transactionId id de la [Transaction] Arzikina générée automatiquement quand [status] passe
 * à [OccurrenceStatus.ACCEPTED]/[OccurrenceStatus.MODIFIED] — `null` tant que l'occurrence reste
 * [OccurrenceStatus.PENDING] ou si elle a été [OccurrenceStatus.REJECTED] (aucune transaction créée
 * dans ce dernier cas).
 * @param processedAt horodatage de la décision utilisateur, `null` tant que [status] reste
 * [OccurrenceStatus.PENDING].
 * @param id 0L tant que l'occurrence n'a pas encore été enregistrée en base.
 */
data class RecurringTransactionOccurrence(
    val id: Long = 0L,
    val recurringTransactionId: Long,
    val scheduledDate: Long,
    val status: OccurrenceStatus = OccurrenceStatus.PENDING,
    val transactionId: Long? = null,
    val processedAt: Long? = null,
    val createdAt: Long
)
