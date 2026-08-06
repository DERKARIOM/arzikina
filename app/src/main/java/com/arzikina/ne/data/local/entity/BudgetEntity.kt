package com.arzikina.ne.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arzikina.ne.domain.model.BudgetPeriod

/**
 * Représentation Room d'un budget. Voir [AccountEntity] pour le raisonnement
 * (le domaine ne connaît jamais cette classe).
 *
 * - `categoryId` en `CASCADE` : à la différence de [TransactionEntity]
 *   (`NO_ACTION`, une transaction a une valeur historique à préserver), un
 *   budget n'est qu'une règle de plafond sans valeur propre une fois sa
 *   catégorie supprimée.
 * - Index `unique` sur `categoryId` : un seul budget actif par catégorie
 *   (voir [Budget]). La couche presentation doit vérifier
 *   [BudgetDao.getByCategoryId] avant de proposer la création d'un budget.
 */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("categoryId", unique = true)]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val categoryId: Long,
    val period: BudgetPeriod,
    val limitAmount: Long,
    val currencyCode: String,
    val createdAt: Long
)
