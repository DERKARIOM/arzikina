package com.arzikina.ne.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arzikina.ne.domain.model.TransactionType

/**
 * Représentation Room d'un [com.arzikina.ne.domain.model.TransactionTemplate] (cahier des charges
 * "Marketplace personnelle") — voir sa doc pour le raisonnement complet.
 *
 * Clés étrangères : mêmes choix que [RecurringTransactionEntity] (`accountId` en `CASCADE`,
 * `categoryId` en `NO_ACTION` par défaut) — mais [categoryId] ici en `NOT NULL` (jamais de
 * transfert pour un modèle, voir la doc du domaine).
 *
 * [userId] : voir [AccountEntity] pour le raisonnement (filtrage direct sans jointure).
 */
@Entity(
    tableName = "transaction_templates",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"]
        )
    ],
    indices = [Index("accountId"), Index("categoryId"), Index("userId"), Index(value = ["syncId"], unique = true)]
)
data class TransactionTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val name: String,
    val type: TransactionType,
    val amount: Long,
    val categoryId: Long,
    val accountId: Long,
    val description: String,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    /** UUID partagé Android/API/MySQL pour une future synchronisation multi-appareils — additif,
     * même convention que toutes les autres entités (voir `docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md`,
     * section 6.3, option B). Posé dès maintenant, non encore activement synchronisé (voir la KDoc
     * de tête de `TransactionTemplateRepositoryImpl`) — même situation que `receipts` à sa création. */
    val syncId: String? = null,
    /** Suppression douce (section 8 du document ci-dessus) : `null` = ligne active. */
    val deletedAt: Long? = null,
    /** Compteur de version optimiste, pour une future détection de conflit côté serveur. */
    val version: Int = 1
)
