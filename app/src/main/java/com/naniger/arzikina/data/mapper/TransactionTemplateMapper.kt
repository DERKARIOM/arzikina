package com.naniger.arzikina.data.mapper

import com.naniger.arzikina.data.local.entity.TransactionTemplateEntity
import com.naniger.arzikina.domain.model.TransactionTemplate

fun TransactionTemplateEntity.toDomain(): TransactionTemplate = TransactionTemplate(
    id = id,
    name = name,
    type = type,
    amount = amount,
    categoryId = categoryId,
    accountId = accountId,
    description = description,
    isFavorite = isFavorite,
    createdAt = createdAt,
    updatedAt = updatedAt,
    defaultHour = defaultHour,
    defaultMinute = defaultMinute
)

/** [userId] : fourni par le repository, jamais par l'appelant. */
fun TransactionTemplate.toEntity(userId: Long): TransactionTemplateEntity = TransactionTemplateEntity(
    id = id,
    userId = userId,
    name = name,
    type = type,
    amount = amount,
    categoryId = categoryId,
    accountId = accountId,
    description = description,
    isFavorite = isFavorite,
    createdAt = createdAt,
    updatedAt = updatedAt,
    defaultHour = defaultHour,
    defaultMinute = defaultMinute
)
