package com.naniger.arzikina.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Représentation Room d'un objectif d'épargne. Voir [AccountEntity] pour le
 * raisonnement (le domaine ne connaît jamais [userId], pas de contrainte SQL
 * `FOREIGN KEY` vers `users`). Aucune autre clé étrangère : un objectif est
 * autonome (voir [com.naniger.arzikina.domain.model.SavingsGoal]) — [userId] est
 * donc ici le SEUL moyen de savoir à qui appartient un objectif (pas de
 * table intermédiaire dont l'appartenance pourrait se déduire indirectement).
 */
@Entity(
    tableName = "savings_goals",
    indices = [Index("userId"), Index(value = ["syncId"], unique = true)]
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
    val createdAt: Long,
    /** UUID partagé Android/API/MySQL pour la synchronisation multi-appareils — additif, voir
     * `docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md` (section 6.3, option B). `null` tant que cette
     * ligne n'a jamais été envoyée au serveur. */
    val syncId: String? = null,
    /** Horodatage de dernière modification, pour la détection de conflit lors de la
     * synchronisation (Last-Write-Wins, section 9 du document ci-dessus). `0L` par défaut, rattrapé
     * à [createdAt] pour les lignes existantes par `MIGRATION_22_23`. */
    val updatedAt: Long = 0L,
    /** Suppression douce (section 8 du document ci-dessus) : `null` = ligne active. */
    val deletedAt: Long? = null,
    /** Compteur de version optimiste, pour la détection de conflit côté serveur (section 9). */
    val version: Int = 1
)
