package com.naniger.arzikina.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.naniger.arzikina.domain.model.LoanReason
import com.naniger.arzikina.domain.model.LoanStatus
import com.naniger.arzikina.domain.model.LoanType
import com.naniger.arzikina.domain.model.RepaymentMode

/**
 * Représentation Room d'un [com.naniger.arzikina.domain.model.Loan].
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
    indices = [Index("personId"), Index("accountId"), Index("userId"), Index(value = ["syncId"], unique = true)]
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
    /** Voir [com.naniger.arzikina.domain.model.Loan.transactionId] : transaction de décaissement initial,
     * toujours renseignée (créée atomiquement avec ce prêt/emprunt). Pas de contrainte
     * `FOREIGN KEY` vers `transactions` (même raisonnement que [LoanPaymentEntity.transactionId]). */
    val transactionId: Long,
    /** UUID partagé Android/API/MySQL pour la synchronisation multi-appareils — additif, voir
     * `docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md` (section 6.3, option B). `null` tant que cette
     * ligne n'a jamais été envoyée au serveur. */
    val syncId: String? = null,
    /** Suppression douce (section 8 du document ci-dessus) : `null` = ligne active. */
    val deletedAt: Long? = null,
    /** Compteur de version optimiste, pour la détection de conflit côté serveur (section 9). Le
     * champ [updatedAt] existant ci-dessus sert désormais aussi de base à cette stratégie
     * Last-Write-Wins. */
    val version: Int = 1
)
