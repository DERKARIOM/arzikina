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

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    icon = icon,
    colorArgb = colorArgb,
    type = type,
    createdAt = createdAt
)
