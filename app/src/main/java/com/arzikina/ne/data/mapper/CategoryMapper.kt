package com.arzikina.ne.data.mapper

import com.arzikina.ne.data.local.entity.CategoryEntity
import com.arzikina.ne.domain.model.Category

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    icon = icon,
    colorArgb = colorArgb,
    type = type,
    createdAt = createdAt
)

/** [userId] : fourni par le repository, jamais par l'appelant. */
fun Category.toEntity(userId: Long): CategoryEntity = CategoryEntity(
    id = id,
    userId = userId,
    name = name,
    icon = icon,
    colorArgb = colorArgb,
    type = type,
    createdAt = createdAt
)
