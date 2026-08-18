package com.arzikina.ne.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arzikina.ne.domain.model.FinancialPlanIcon
import com.arzikina.ne.domain.model.PlanPeriodType
import com.arzikina.ne.domain.model.PlanStatus

/**
 * Représentation Room d'une [com.arzikina.ne.domain.model.FinancialPlan]. Voir [AccountEntity]
 * pour le raisonnement (le domaine ne connaît jamais cette classe).
 *
 * AUCUNE `ForeignKey` vers `accounts` (contrairement à [LoanEntity]) : une planification n'est
 * reliée à aucun compte précis, voir la doc de [com.arzikina.ne.domain.model.FinancialPlan.availableAmount].
 *
 * [userId] : voir [AccountEntity] pour le raisonnement (pas de contrainte SQL vers `users`,
 * domaine non concerné) — indexé pour un filtrage direct sans jointure.
 */
@Entity(
    tableName = "financial_plans",
    indices = [Index("userId")]
)
data class FinancialPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val name: String,
    val description: String?,
    val availableAmount: Long,
    val targetAmount: Long?,
    val periodType: PlanPeriodType,
    val startDate: Long?,
    val endDate: Long?,
    val icon: FinancialPlanIcon,
    val colorArgb: Long,
    val status: PlanStatus,
    val createdAt: Long,
    val updatedAt: Long
)
