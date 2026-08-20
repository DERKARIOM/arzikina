package com.arzikina.ne.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Représentation Room d'un [com.arzikina.ne.domain.model.Receipt] — voir sa doc pour le
 * raisonnement complet sur chaque champ (notamment [fileName] vs [localPath]).
 *
 * Aucune clé étrangère : un reçu n'est rattaché à aucun compte/catégorie/transaction (indépendant
 * du reste du modèle financier, voir cahier des charges "Gestion des reçus").
 *
 * [userId] : voir [AccountEntity] pour le raisonnement (filtrage direct sans jointure, isolation
 * multi-utilisateurs).
 */
@Entity(
    tableName = "receipts",
    indices = [Index("userId")]
)
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val fileName: String,
    val localPath: String,
    val receivedAt: Long,
    val fileSize: Long,
    val mimeType: String,
    val sourceApp: String?,
    val sourceName: String?,
    val amountMinor: Long?,
    val createdAt: Long,
    val updatedAt: Long
)
