package com.arzikina.ne.data.mapper

import com.arzikina.ne.data.local.entity.BudgetEntity
import com.arzikina.ne.domain.model.Budget

fun BudgetEntity.toDomain(): Budget = Budget(
    id = id,
    categoryId = categoryId,
    period = period,
    limitAmount = limitAmount,
    currencyCode = currencyCode,
    createdAt = createdAt
)

fun Budget.toEntity(): BudgetEntity = BudgetEntity(
    id = id,
    categoryId = categoryId,
    period = period,
    limitAmount = limitAmount,
    currencyCode = currencyCode,
    createdAt = createdAt
)
