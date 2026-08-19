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
 * - Index sur `categoryId` : **non unique depuis la version 19** (voir
 *   [com.arzikina.ne.data.local.database.MIGRATION_18_19]). Avant cette
 *   version, un seul budget existait jamais par catégorie ; la fonctionnalité
 *   "période fixe" autorise désormais plusieurs budgets successifs sur la
 *   même catégorie (ex. "Alimentation" d'août, puis "Alimentation" de
 *   septembre). La règle "un seul budget **actif** par catégorie" (À venir ou
 *   En cours) reste appliquée, mais uniquement côté présentation (voir
 *   `BudgetFormViewModel.availableCategories`), plus en base.
 * - [userId] : voir [AccountEntity] (pas de contrainte SQL vers `users`).
 * - [startDate]/[endDate] : `null` pour un budget récurrent (legacy, voir
 *   [Budget]), tous deux non nuls pour un budget à période fixe. Jamais un
 *   seul des deux renseigné.
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
    indices = [Index("categoryId"), Index("userId")]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val categoryId: Long,
    val period: BudgetPeriod,
    val limitAmount: Long,
    val currencyCode: String,
    val createdAt: Long,
    val startDate: Long? = null,
    val endDate: Long? = null
)
