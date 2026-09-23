package com.naniger.arzikina.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.naniger.arzikina.domain.model.FinancialPlanIcon
import com.naniger.arzikina.domain.model.PlanPeriodType
import com.naniger.arzikina.domain.model.PlanStatus

/**
 * Représentation Room d'une [com.naniger.arzikina.domain.model.FinancialPlan]. Voir [AccountEntity]
 * pour le raisonnement (le domaine ne connaît jamais cette classe).
 *
 * AUCUNE `ForeignKey` vers `accounts` (contrairement à [LoanEntity]) : une planification n'est
 * reliée à aucun compte précis, voir la doc de [com.naniger.arzikina.domain.model.FinancialPlan.availableAmount].
 *
 * [userId] : voir [AccountEntity] pour le raisonnement (pas de contrainte SQL vers `users`,
 * domaine non concerné) — indexé pour un filtrage direct sans jointure.
 */
@Entity(
    tableName = "financial_plans",
    indices = [Index("userId"), Index(value = ["syncId"], unique = true)]
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
    val updatedAt: Long,
    /** UUID partagé Android/API/MySQL pour la synchronisation multi-appareils — additif, voir
     * `docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md` (section 6.3, option B). `null` tant que cette
     * ligne n'a jamais été envoyée au serveur. */
    val syncId: String? = null,
    /** Suppression douce (section 8 du document ci-dessus) : `null` = ligne active. */
    val deletedAt: Long? = null,
    /** Compteur de version optimiste, pour la détection de conflit côté serveur (section 9). Le
     * champ [updatedAt] existant ci-dessus sert désormais aussi de base à cette stratégie
     * Last-Write-Wins. */
    val version: Int = 1
)
