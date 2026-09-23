package com.naniger.arzikina.data.mapper

import com.naniger.arzikina.data.local.entity.BudgetEntity
import com.naniger.arzikina.domain.model.Budget

fun BudgetEntity.toDomain(): Budget = Budget(
    id = id,
    categoryId = categoryId,
    period = period,
    limitAmount = limitAmount,
    currencyCode = currencyCode,
    createdAt = createdAt,
    startDate = startDate,
    endDate = endDate
)

/** [userId] : fourni par le repository, jamais par l'appelant. */
fun Budget.toEntity(userId: Long): BudgetEntity = BudgetEntity(
    id = id,
    userId = userId,
    categoryId = categoryId,
    period = period,
    limitAmount = limitAmount,
    currencyCode = currencyCode,
    createdAt = createdAt,
    startDate = startDate,
    endDate = endDate
)
