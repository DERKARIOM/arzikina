package com.arzikina.ne.data.mapper

import com.arzikina.ne.data.local.entity.FinancialPlanItemEntity
import com.arzikina.ne.domain.model.FinancialPlanItem

fun FinancialPlanItemEntity.toDomain(): FinancialPlanItem = FinancialPlanItem(
    id = id,
    planId = planId,
    name = name,
    amount = amount,
    actualAmount = actualAmount,
    categoryId = categoryId,
    description = description,
    plannedDate = plannedDate,
    priority = priority,
    status = status,
    transactionId = transactionId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/** [userId] : fourni par le repository, jamais par l'appelant. */
fun FinancialPlanItem.toEntity(userId: Long): FinancialPlanItemEntity = FinancialPlanItemEntity(
    id = id,
    userId = userId,
    planId = planId,
    name = name,
    amount = amount,
    actualAmount = actualAmount,
    categoryId = categoryId,
    description = description,
    plannedDate = plannedDate,
    priority = priority,
    status = status,
    transactionId = transactionId,
    createdAt = createdAt,
    updatedAt = updatedAt
)
