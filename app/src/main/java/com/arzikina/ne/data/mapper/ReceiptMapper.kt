package com.arzikina.ne.data.mapper

import com.arzikina.ne.data.local.entity.ReceiptEntity
import com.arzikina.ne.domain.model.Receipt

fun ReceiptEntity.toDomain(): Receipt = Receipt(
    id = id,
    fileName = fileName,
    localPath = localPath,
    receivedAt = receivedAt,
    fileSize = fileSize,
    mimeType = mimeType,
    sourceApp = sourceApp,
    sourceName = sourceName,
    amountMinor = amountMinor,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/** [userId] : fourni par le repository, jamais par l'appelant. */
fun Receipt.toEntity(userId: Long): ReceiptEntity = ReceiptEntity(
    id = id,
    userId = userId,
    fileName = fileName,
    localPath = localPath,
    receivedAt = receivedAt,
    fileSize = fileSize,
    mimeType = mimeType,
    sourceApp = sourceApp,
    sourceName = sourceName,
    amountMinor = amountMinor,
    createdAt = createdAt,
    updatedAt = updatedAt
)
