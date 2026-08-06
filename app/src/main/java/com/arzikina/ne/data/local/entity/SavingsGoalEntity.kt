package com.arzikina.ne.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Représentation Room d'un objectif d'épargne. Voir [AccountEntity] pour le
 * raisonnement (le domaine ne connaît jamais cette classe). Pas de clé
 * étrangère : un objectif est autonome (voir [com.arzikina.ne.domain.model.SavingsGoal]).
 */
@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val targetAmount: Long,
    val currentAmount: Long,
    val currencyCode: String,
    val deadline: Long?,
    val createdAt: Long
)
