package com.arzikina.ne.data.mapper

import com.arzikina.ne.data.local.entity.RecurringTransactionEntity
import com.arzikina.ne.domain.model.RecurringTransaction

fun RecurringTransactionEntity.toDomain(): RecurringTransaction = RecurringTransaction(
    id = id,
    type = type,
    amount = amount,
    accountId = accountId,
    categoryId = categoryId,
    description = description,
    paymentMethod = paymentMethod,
    startDate = startDate,
    endDate = endDate,
    frequency = frequency,
    nextExecutionDate = nextExecutionDate,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
    triggerHour = triggerHour,
    triggerMinute = triggerMinute
)

/** [userId] : fourni par le repository, jamais par l'appelant. */
fun RecurringTransaction.toEntity(userId: Long): RecurringTransactionEntity = RecurringTransactionEntity(
    id = id,
    userId = userId,
    type = type,
    amount = amount,
    accountId = accountId,
    categoryId = categoryId,
    description = description,
    paymentMethod = paymentMethod,
    startDate = startDate,
    endDate = endDate,
    frequency = frequency,
    nextExecutionDate = nextExecutionDate,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
    triggerHour = triggerHour,
    triggerMinute = triggerMinute
)
