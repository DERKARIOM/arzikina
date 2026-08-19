package com.arzikina.ne.data.mapper

import com.arzikina.ne.data.local.entity.BudgetEntity
import com.arzikina.ne.domain.model.Budget

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
