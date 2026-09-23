package com.naniger.arzikina.data.mapper

import com.naniger.arzikina.data.local.entity.ReceiptEntity
import com.naniger.arzikina.domain.model.Receipt

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
