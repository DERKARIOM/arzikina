package com.arzikina.ne.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arzikina.ne.domain.model.CategoryIcon
import com.arzikina.ne.domain.model.TransactionType

/**
 * Représentation Room d'une catégorie. Voir [com.arzikina.ne.data.local.entity.AccountEntity]
 * pour le raisonnement (le domaine ne connaît jamais [userId], pas de
 * contrainte SQL `FOREIGN KEY` vers `users`).
 */
@Entity(
    tableName = "categories",
    indices = [Index("userId")]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val name: String,
    val icon: CategoryIcon,
    val colorArgb: Long,
    val type: TransactionType,
    val createdAt: Long
)
