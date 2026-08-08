package com.arzikina.ne.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arzikina.ne.domain.model.PaymentMethod
import com.arzikina.ne.domain.model.TransactionType

/**
 * Représentation Room d'une transaction. Voir [AccountEntity] pour le
 * raisonnement (le domaine ne connaît jamais cette classe).
 *
 * Clés étrangères :
 * - `accountId` en `CASCADE` : supprimer un compte purge son historique.
 * - `transferAccountId` (voir [com.arzikina.ne.domain.model.Transaction.transferAccountId])
 *   en `CASCADE` également, pour la même raison : un transfert n'a plus de
 *   sens si l'un OU l'autre des deux comptes disparaît.
 * - `categoryId` en `NO_ACTION` (défaut) : SQLite refuse la suppression
 *   d'une catégorie encore référencée par une transaction, pour préserver
 *   l'intégrité des données. Les couches supérieures doivent intercepter
 *   cette contrainte et informer l'utilisateur plutôt que de laisser
 *   planter l'application. `NULL` pour un transfert (voir [TransactionType.TRANSFER]),
 *   donc absent de cette contrainte dans ce cas (voir migration 9→10, qui a
 *   dû recréer la table pour retirer le `NOT NULL` d'origine sur cette colonne).
 *
 * Index sur les trois clés étrangères : requêtes fréquentes (historique par
 * compte, statistiques par catégorie) et exigence Room pour éviter un
 * balayage complet de la table lors des vérifications de contrainte.
 *
 * [userId] : voir [AccountEntity] pour le raisonnement (pas de contrainte
 * SQL vers `users`, domaine non concerné). Redondant avec la propriété du
 * compte/de la catégorie référencés (qui appartiennent déjà à un seul
 * utilisateur), mais explicite ici pour un filtrage direct sans jointure.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["transferAccountId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"]
        )
    ],
    indices = [Index("accountId"), Index("transferAccountId"), Index("categoryId"), Index("userId")]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val amount: Long,
    val type: TransactionType,
    val accountId: Long,
    /** Voir [com.arzikina.ne.domain.model.Transaction.transferAccountId] : `NULL` sauf pour un transfert. */
    val transferAccountId: Long?,
    /** Voir [com.arzikina.ne.domain.model.Transaction.categoryId] : `NULL` uniquement pour un transfert. */
    val categoryId: Long?,
    val date: Long,
    val description: String,
    val receiptPhotoUri: String?,
    val latitude: Double?,
    val longitude: Double?,
    /** Voir [com.arzikina.ne.domain.model.Transaction.paymentMethod] : `NULL` = non précisé. */
    val paymentMethod: PaymentMethod?,
    val createdAt: Long
)
