package com.arzikina.ne.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arzikina.ne.domain.model.PlanItemPriority
import com.arzikina.ne.domain.model.PlanItemStatus

/**
 * Représentation Room d'une [com.arzikina.ne.domain.model.FinancialPlanItem].
 *
 * Clés étrangères :
 * - `planId` en `CASCADE` : supprimer une planification purge ses dépenses prévues (voir
 *   [FinancialPlanEntity]).
 * - `categoryId` en `NO_ACTION` (défaut) — même raisonnement que [TransactionEntity.categoryId] :
 *   SQLite refuse la suppression d'une catégorie encore référencée par une dépense prévue.
 *
 * `transactionId` : volontairement SANS `ForeignKey` — voir la doc de
 * [com.arzikina.ne.domain.model.FinancialPlanItem.transactionId] (même raisonnement que
 * [TransactionEntity.feeTransactionId]).
 *
 * [userId] : redondant avec celui de la planification parente, mais explicite pour un filtrage
 * direct sans jointure — même principe que partout ailleurs dans le projet (voir [AccountEntity]).
 */
@Entity(
    tableName = "financial_plan_items",
    foreignKeys = [
        ForeignKey(
            entity = FinancialPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"]
        )
    ],
    indices = [Index("planId"), Index("categoryId"), Index("userId"), Index(value = ["syncId"], unique = true)]
)
data class FinancialPlanItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val planId: Long,
    val name: String,
    val amount: Long,
    val actualAmount: Long?,
    val categoryId: Long?,
    val description: String?,
    val plannedDate: Long?,
    val priority: PlanItemPriority,
    val status: PlanItemStatus,
    val transactionId: Long?,
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
