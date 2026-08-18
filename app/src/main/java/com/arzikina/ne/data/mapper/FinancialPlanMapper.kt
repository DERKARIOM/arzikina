package com.arzikina.ne.data.mapper

import com.arzikina.ne.data.local.entity.FinancialPlanEntity
import com.arzikina.ne.domain.model.FinancialPlan

fun FinancialPlanEntity.toDomain(): FinancialPlan = FinancialPlan(
    id = id,
    name = name,
    description = description,
    availableAmount = availableAmount,
    targetAmount = targetAmount,
    periodType = periodType,
    startDate = startDate,
    endDate = endDate,
    icon = icon,
    colorArgb = colorArgb,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/** [userId] : fourni par le repository, jamais par l'appelant. */
fun FinancialPlan.toEntity(userId: Long): FinancialPlanEntity = FinancialPlanEntity(
    id = id,
    userId = userId,
    name = name,
    description = description,
    availableAmount = availableAmount,
    targetAmount = targetAmount,
    periodType = periodType,
    startDate = startDate,
    endDate = endDate,
    icon = icon,
    colorArgb = colorArgb,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt
)
