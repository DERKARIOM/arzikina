package com.arzikina.ne.data.mapper

import com.arzikina.ne.data.local.entity.LoanEntity
import com.arzikina.ne.domain.model.Loan

fun LoanEntity.toDomain(): Loan = Loan(
    id = id,
    personId = personId,
    accountId = accountId,
    type = type,
    amount = amount,
    amountRepaid = amountRepaid,
    remainingAmount = remainingAmount,
    startDate = startDate,
    dueDate = dueDate,
    reason = reason,
    reasonCustomText = reasonCustomText,
    repaymentMode = repaymentMode,
    description = description,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
    transactionId = transactionId
)

/** [userId] : fourni par le repository, jamais par l'appelant. */
fun Loan.toEntity(userId: Long): LoanEntity = LoanEntity(
    id = id,
    userId = userId,
    personId = personId,
    accountId = accountId,
    type = type,
    amount = amount,
    amountRepaid = amountRepaid,
    remainingAmount = remainingAmount,
    startDate = startDate,
    dueDate = dueDate,
    reason = reason,
    reasonCustomText = reasonCustomText,
    repaymentMode = repaymentMode,
    description = description,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
    transactionId = transactionId
)
