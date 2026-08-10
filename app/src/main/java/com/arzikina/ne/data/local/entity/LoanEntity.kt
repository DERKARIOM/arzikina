package com.arzikina.ne.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arzikina.ne.domain.model.LoanReason
import com.arzikina.ne.domain.model.LoanStatus
import com.arzikina.ne.domain.model.LoanType
import com.arzikina.ne.domain.model.RepaymentMode

/**
 * Représentation Room d'un [com.arzikina.ne.domain.model.Loan].
 *
 * Clés étrangères en `CASCADE` (voir [TransactionEntity] pour le même principe déjà appliqué à
 * `accountId`) :
 * - `personId` : supprimer une personne supprime l'historique de ses prêts/emprunts.
 * - `accountId` : supprimer un compte supprime les prêts/emprunts qui lui sont associés, comme il
 *   purge déjà l'historique des transactions de ce compte.
 *
 * [userId] : voir [AccountEntity] pour le raisonnement (redondant avec la propriété de
 * `personId`/`accountId`, mais explicite ici pour un filtrage direct sans jointure).
 */
@Entity(
    tableName = "loans",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("personId"), Index("accountId"), Index("userId")]
)
data class LoanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val personId: Long,
    val accountId: Long,
    val type: LoanType,
    val amount: Long,
    val amountRepaid: Long,
    val remainingAmount: Long,
    val startDate: Long,
    val dueDate: Long,
    val reason: LoanReason,
    val reasonCustomText: String?,
    val repaymentMode: RepaymentMode,
    val description: String,
    val status: LoanStatus,
    val createdAt: Long,
    val updatedAt: Long,
    /** Voir [com.arzikina.ne.domain.model.Loan.transactionId] : transaction de décaissement initial,
     * toujours renseignée (créée atomiquement avec ce prêt/emprunt). Pas de contrainte
     * `FOREIGN KEY` vers `transactions` (même raisonnement que [LoanPaymentEntity.transactionId]). */
    val transactionId: Long
)
