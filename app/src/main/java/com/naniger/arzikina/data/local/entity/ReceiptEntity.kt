package com.naniger.arzikina.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Représentation Room d'un [com.naniger.arzikina.domain.model.Receipt] — voir sa doc pour le
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
    indices = [Index("userId"), Index(value = ["syncId"], unique = true)]
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
    val updatedAt: Long,
    /** UUID partagé Android/API/MySQL pour la synchronisation multi-appareils — additif, voir
     * `docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md` (section 6.3, option B). `null` tant que cette
     * ligne n'a jamais été envoyée au serveur. Les octets du PDF lui-même ne sont JAMAIS synchronisés
     * via cette ligne : seul le fichier suit un chemin d'upload séparé côté serveur (section 4). */
    val syncId: String? = null,
    /** Suppression douce (section 8 du document ci-dessus) : `null` = ligne active. */
    val deletedAt: Long? = null,
    /** Compteur de version optimiste, pour la détection de conflit côté serveur (section 9). Le
     * champ [updatedAt] existant ci-dessus sert désormais aussi de base à cette stratégie
     * Last-Write-Wins. */
    val version: Int = 1
)
