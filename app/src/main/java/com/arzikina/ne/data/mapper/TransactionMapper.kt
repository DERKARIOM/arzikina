package com.arzikina.ne.data.mapper

import com.arzikina.ne.data.local.entity.TransactionEntity
import com.arzikina.ne.domain.model.Transaction

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    amount = amount,
    type = type,
    accountId = accountId,
    transferAccountId = transferAccountId,
    categoryId = categoryId,
    date = date,
    description = description,
    receiptPhotoUri = receiptPhotoUri,
    latitude = latitude,
    longitude = longitude,
    paymentMethod = paymentMethod,
    createdAt = createdAt,
    feeTransactionId = feeTransactionId,
    feeType = feeType
)

/** [userId] : fourni par le repository, jamais par l'appelant. */
fun Transaction.toEntity(userId: Long): TransactionEntity = TransactionEntity(
    id = id,
    userId = userId,
    amount = amount,
    type = type,
    accountId = accountId,
    transferAccountId = transferAccountId,
    categoryId = categoryId,
    date = date,
    description = description,
    receiptPhotoUri = receiptPhotoUri,
    latitude = latitude,
    longitude = longitude,
    paymentMethod = paymentMethod,
    createdAt = createdAt,
    feeTransactionId = feeTransactionId,
    feeType = feeType
)
