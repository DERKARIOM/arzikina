package com.arzikina.ne.data.mapper

import com.arzikina.ne.data.local.entity.RecurringTransactionOccurrenceEntity
import com.arzikina.ne.domain.model.RecurringTransactionOccurrence

fun RecurringTransactionOccurrenceEntity.toDomain(): RecurringTransactionOccurrence = RecurringTransactionOccurrence(
    id = id,
    recurringTransactionId = recurringTransactionId,
    scheduledDate = scheduledDate,
    status = status,
    transactionId = transactionId,
    processedAt = processedAt,
    createdAt = createdAt
)

/** [userId] : fourni par le repository, jamais par l'appelant. */
fun RecurringTransactionOccurrence.toEntity(userId: Long): RecurringTransactionOccurrenceEntity = RecurringTransactionOccurrenceEntity(
    id = id,
    userId = userId,
    recurringTransactionId = recurringTransactionId,
    scheduledDate = scheduledDate,
    status = status,
    transactionId = transactionId,
    processedAt = processedAt,
    createdAt = createdAt
)
