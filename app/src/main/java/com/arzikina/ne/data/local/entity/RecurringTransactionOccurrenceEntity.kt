package com.arzikina.ne.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arzikina.ne.domain.model.OccurrenceStatus

/**
 * Représentation Room d'une [com.arzikina.ne.domain.model.RecurringTransactionOccurrence] :
 * l'exécution réelle d'une [RecurringTransactionEntity] à une date donnée (voir sa doc pour le
 * raisonnement de cette séparation).
 *
 * `recurringTransactionId` en `CASCADE` : supprimer une règle récurrente supprime tout son
 * historique d'occurrences (même principe que [LoanPaymentEntity.loanId]).
 *
 * `transactionId` n'a volontairement PAS de contrainte `FOREIGN KEY` vers `transactions` (même
 * raisonnement que [LoanPaymentEntity.transactionId]) : nullable ici, contrairement à
 * `LoanPaymentEntity`, car une occurrence existe AVANT d'être traitée (statut [OccurrenceStatus.PENDING],
 * pas encore de transaction réelle) — seul un statut [OccurrenceStatus.ACCEPTED]/[OccurrenceStatus.MODIFIED]
 * la renseigne.
 *
 * Index unique `(recurringTransactionId, scheduledDate)` : une même règle ne doit jamais avoir deux
 * occurrences pour la même date — protection au niveau base contre une double génération (voir
 * `RecurringTransactionRepositoryImpl.generateMissingOccurrences`, appelée à la fois à l'ouverture
 * de l'app et par le futur `Worker` périodique).
 */
@Entity(
    tableName = "recurring_transaction_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = RecurringTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurringTransactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("userId"),
        Index(value = ["recurringTransactionId", "scheduledDate"], unique = true)
    ]
)
data class RecurringTransactionOccurrenceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val recurringTransactionId: Long,
    val scheduledDate: Long,
    val status: OccurrenceStatus,
    /** Renseigné uniquement après [OccurrenceStatus.ACCEPTED]/[OccurrenceStatus.MODIFIED] (voir la doc de classe). */
    val transactionId: Long?,
    /** Horodatage de la décision utilisateur (Enregistrer/Modifier/Rejeter), `NULL` tant que
     * [status] reste [OccurrenceStatus.PENDING]. */
    val processedAt: Long?,
    val createdAt: Long
)
