package com.arzikina.ne.data.mapper

import com.arzikina.ne.data.local.entity.SavingsGoalEntity
import com.arzikina.ne.domain.model.SavingsGoal

fun SavingsGoalEntity.toDomain(): SavingsGoal = SavingsGoal(
    id = id,
    name = name,
    targetAmount = targetAmount,
    currentAmount = currentAmount,
    currencyCode = currencyCode,
    deadline = deadline,
    createdAt = createdAt
)

/** [userId] : fourni par le repository, jamais par l'appelant. */
fun SavingsGoal.toEntity(userId: Long): SavingsGoalEntity = SavingsGoalEntity(
    id = id,
    userId = userId,
    name = name,
    targetAmount = targetAmount,
    currentAmount = currentAmount,
    currencyCode = currencyCode,
    deadline = deadline,
    createdAt = createdAt
)
