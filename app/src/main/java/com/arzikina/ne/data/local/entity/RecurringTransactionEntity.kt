package com.arzikina.ne.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arzikina.ne.domain.model.PaymentMethod
import com.arzikina.ne.domain.model.RecurringFrequency
import com.arzikina.ne.domain.model.TransactionType

/**
 * Représentation Room d'une [com.arzikina.ne.domain.model.RecurringTransaction] : la RÈGLE qui
 * décrit une transaction à reconduire, pas une exécution réelle (voir
 * [RecurringTransactionOccurrenceEntity] pour cette dernière) — même séparation modèle/occurrence
 * que [LoanEntity]/[LoanPaymentEntity].
 *
 * Clés étrangères : mêmes choix que [TransactionEntity] (`accountId` en `CASCADE`, `categoryId`
 * en `NO_ACTION` par défaut, `NULL` réservé à un futur type transfert — voir [TransactionEntity]
 * pour le raisonnement complet, volontairement identique ici).
 *
 * [userId] : voir [AccountEntity] pour le raisonnement (filtrage direct sans jointure).
 */
@Entity(
    tableName = "recurring_transactions",
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
    indices = [Index("accountId"), Index("categoryId"), Index("userId")]
)
data class RecurringTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val type: TransactionType,
    val amount: Long,
    val accountId: Long,
    /** Voir [TransactionEntity.categoryId] : `NULL` uniquement réservé à un futur type transfert,
     * toujours renseigné pour un revenu/une dépense. */
    val categoryId: Long?,
    val description: String,
    val paymentMethod: PaymentMethod?,
    val startDate: Long,
    /** `NULL` = pas de date de fin (se reconduit indéfiniment tant que [isActive]). */
    val endDate: Long?,
    val frequency: RecurringFrequency,
    /**
     * Date de la PROCHAINE occurrence à générer (voir
     * `RecurringTransactionRepositoryImpl.generateMissingOccurrences`). Avancée à chaque occurrence
     * générée — jamais recalculée depuis [startDate] à chaque lecture, pour ne pas re-parcourir tout
     * l'historique d'une règle ancienne à chaque ouverture de l'app.
     */
    val nextExecutionDate: Long,
    /** `false` = la règle ne génère plus de nouvelles occurrences (arrêtée par l'utilisateur), mais
     * son historique reste consultable — jamais supprimée pour autant. */
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
