package com.naniger.arzikina.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Représentation Room d'un [com.naniger.arzikina.domain.model.LoanPayment].
 *
 * `loanId` en `CASCADE` : supprimer un prêt/emprunt supprime son historique de remboursements.
 * `accountId` en `CASCADE` : même principe que [LoanEntity.accountId]/[TransactionEntity.accountId]
 * (indépendant du compte associé au prêt lui-même — un remboursement peut transiter par un compte
 * différent de celui utilisé lors du prêt initial).
 *
 * `transactionId` n'a volontairement PAS de contrainte `FOREIGN KEY` vers `transactions` : voir
 * [com.naniger.arzikina.domain.model.LoanPayment] pour le raisonnement (écriture atomique côté
 * repository, la transaction liée existe toujours avant l'écriture de ce paiement).
 */
@Entity(
    tableName = "loan_payments",
    foreignKeys = [
        ForeignKey(
            entity = LoanEntity::class,
            parentColumns = ["id"],
            childColumns = ["loanId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("loanId"), Index("accountId"), Index("userId"), Index(value = ["syncId"], unique = true)]
)
data class LoanPaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val loanId: Long,
    val accountId: Long,
    val amount: Long,
    val date: Long,
    val note: String,
    val transactionId: Long,
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
