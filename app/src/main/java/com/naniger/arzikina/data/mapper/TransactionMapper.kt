package com.naniger.arzikina.data.mapper

import com.naniger.arzikina.data.local.entity.TransactionEntity
import com.naniger.arzikina.domain.model.Transaction

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
    feeType = feeType,
    receiptId = receiptId
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
    feeType = feeType,
    receiptId = receiptId
)
