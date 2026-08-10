package com.arzikina.ne.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Représentation Room d'un [com.arzikina.ne.domain.model.LoanPayment].
 *
 * `loanId` en `CASCADE` : supprimer un prêt/emprunt supprime son historique de remboursements.
 * `accountId` en `CASCADE` : même principe que [LoanEntity.accountId]/[TransactionEntity.accountId]
 * (indépendant du compte associé au prêt lui-même — un remboursement peut transiter par un compte
 * différent de celui utilisé lors du prêt initial).
 *
 * `transactionId` n'a volontairement PAS de contrainte `FOREIGN KEY` vers `transactions` : voir
 * [com.arzikina.ne.domain.model.LoanPayment] pour le raisonnement (écriture atomique côté
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
    indices = [Index("loanId"), Index("accountId"), Index("userId")]
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
    val createdAt: Long
)
