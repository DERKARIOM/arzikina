package com.arzikina.ne.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Représentation Room d'une [com.arzikina.ne.domain.model.Person]. Voir [AccountEntity] pour le
 * raisonnement (le domaine ne connaît jamais [userId], pas de contrainte SQL `FOREIGN KEY` vers
 * `users`).
 */
@Entity(
    tableName = "persons",
    indices = [Index("userId")]
)
data class PersonEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val name: String,
    val phone: String?,
    val createdAt: Long
)
