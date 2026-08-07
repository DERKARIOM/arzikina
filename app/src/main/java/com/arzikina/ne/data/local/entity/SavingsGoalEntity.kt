package com.arzikina.ne.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Représentation Room d'un objectif d'épargne. Voir [AccountEntity] pour le
 * raisonnement (le domaine ne connaît jamais [userId], pas de contrainte SQL
 * `FOREIGN KEY` vers `users`). Aucune autre clé étrangère : un objectif est
 * autonome (voir [com.arzikina.ne.domain.model.SavingsGoal]) — [userId] est
 * donc ici le SEUL moyen de savoir à qui appartient un objectif (pas de
 * table intermédiaire dont l'appartenance pourrait se déduire indirectement).
 */
@Entity(
    tableName = "savings_goals",
    indices = [Index("userId")]
)
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val name: String,
    val targetAmount: Long,
    val currentAmount: Long,
    val currencyCode: String,
    val deadline: Long?,
    val createdAt: Long
)
