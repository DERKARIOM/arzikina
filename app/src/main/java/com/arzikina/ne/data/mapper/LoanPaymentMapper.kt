package com.arzikina.ne.data.mapper

import com.arzikina.ne.data.local.entity.LoanPaymentEntity
import com.arzikina.ne.domain.model.LoanPayment

fun LoanPaymentEntity.toDomain(): LoanPayment = LoanPayment(
    id = id,
    loanId = loanId,
    accountId = accountId,
    amount = amount,
    date = date,
    note = note,
    transactionId = transactionId,
    createdAt = createdAt
)

/** [userId] : fourni par le repository, jamais par l'appelant. */
fun LoanPayment.toEntity(userId: Long): LoanPaymentEntity = LoanPaymentEntity(
    id = id,
    userId = userId,
    loanId = loanId,
    accountId = accountId,
    amount = amount,
    date = date,
    note = note,
    transactionId = transactionId,
    createdAt = createdAt
)
