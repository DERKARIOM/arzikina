package com.naniger.arzikina.data.repository

import com.naniger.arzikina.data.local.dao.CategoryDao
import com.naniger.arzikina.data.local.dao.LoanDao
import com.naniger.arzikina.data.local.dao.TransactionDao
import com.naniger.arzikina.data.local.database.SystemCategoryResolver
import com.naniger.arzikina.data.local.entity.LoanEntity
import com.naniger.arzikina.data.local.entity.TransactionEntity
import com.naniger.arzikina.data.mapper.toDomain
import com.naniger.arzikina.data.mapper.toEntity
import com.naniger.arzikina.domain.model.LoanGiftException
import com.naniger.arzikina.domain.model.LoanStatus
import com.naniger.arzikina.domain.model.SyncOperation
import com.naniger.arzikina.domain.model.Transaction
import com.naniger.arzikina.domain.model.giftCategoryKey
import com.naniger.arzikina.domain.model.giftTransactionType
import com.naniger.arzikina.domain.model.planGiftConversion
import java.util.UUID
import javax.inject.Inject

/**
 * Écritures Room de « Transformer un prêt/emprunt en cadeau » (voir
 * `LoanRepository.convertToGift` et la règle comptable dans `domain/model/LoanGift.kt`).
 *
 * Extrait de `LoanRepositoryImpl` pour deux raisons :
 * - TESTABILITÉ : aucune dépendance à `RoomDatabase.withTransaction`, testable avec de simples
 *   DAO simulés (voir `LoanGiftConverterTest`) ;
 * - TAILLE : `LoanRepositoryImpl` concentre déjà création, remboursements et suppressions.
 *
 * CONTRAT : [convert] DOIT être appelé à l'intérieur d'une transaction Room (c'est
 * `LoanRepositoryImpl.convertToGift` qui l'ouvre) et n'enfile AUCUNE synchronisation de
 * transaction/prêt lui-même — il retourne les écritures à enfiler APRÈS commit, comme le reste du
 * projet. Seule exception : une catégorie « Cadeaux » recréée silencieusement est enfilée par
 * [SystemCategoryResolver], comme pour les catégories Prêts/Frais.
 */
class LoanGiftConverter @Inject constructor(
    private val loanDao: LoanDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val categorySyncEnqueuer: CategorySyncEnqueuer
) {

    /**
     * @param giftTransactionId transaction cadeau (= décaissement reclassé, ou nouvelle ligne).
     * @param transactionOps écritures de transactions à enfiler, dans l'ordre.
     * @param giftedLoan prêt/emprunt clôturé, à enfiler en `UPDATE` APRÈS [transactionOps].
     */
    data class Result(
        val giftTransactionId: Long,
        val transactionOps: List<Pair<TransactionEntity, SyncOperation>>,
        val giftedLoan: LoanEntity
    )

    /**
     * @throws LoanGiftException.NotConvertible prêt/emprunt introuvable, déjà remboursé ou déjà
     * transformé (voir `canConvertToGift`) — rien n'a été écrit.
     */
    suspend fun convert(loanId: Long, description: String, userId: Long, now: Long): Result {
        val loan = loanDao.getById(loanId, userId) ?: throw LoanGiftException.NotConvertible(loanId)
        // Règle métier unique (domaine) : refuse un prêt déjà remboursé/transformé et calcule le
        // reclassement à partir des montants SOURCES, jamais du `remainingAmount` dénormalisé.
        val plan = loan.toDomain().planGiftConversion(now)
        val disbursement = transactionDao.getById(loan.transactionId, userId)
            ?: error("Transaction de décaissement introuvable (loanId=$loanId).")
        val giftCategory = SystemCategoryResolver.resolve(categoryDao, categorySyncEnqueuer, loan.type.giftCategoryKey, userId)
        val transactionOps = mutableListOf<Pair<TransactionEntity, SyncOperation>>()

        val giftTransactionId = if (plan.reusesDisbursementTransaction) {
            // Rien de remboursé : reclassement EN PLACE (même id, même syncId, même montant, même
            // compte, même date) — aucune nouvelle ligne, aucun doublon possible.
            val reclassified = disbursement.copy(
                categoryId = giftCategory.id,
                description = description,
                syncId = disbursement.syncId ?: UUID.randomUUID().toString(),
                updatedAt = now
            )
            transactionDao.upsert(reclassified)
            transactionOps += reclassified to SyncOperation.UPDATE
            disbursement.id
        } else {
            // Remboursement partiel : le décaissement ne garde que la part remboursée...
            val reduced = disbursement.copy(
                amount = plan.disbursementAmountAfter,
                syncId = disbursement.syncId ?: UUID.randomUUID().toString(),
                updatedAt = now
            )
            transactionDao.upsert(reduced)
            transactionOps += reduced to SyncOperation.UPDATE
            // ...et UNE transaction cadeau porte le reste, même compte, même date : la somme des
            // deux reste égale au montant d'origine, le solde ne bouge pas.
            val gift = Transaction(
                amount = plan.giftAmount,
                type = loan.type.giftTransactionType,
                accountId = disbursement.accountId,
                categoryId = giftCategory.id,
                date = disbursement.date,
                description = description,
                createdAt = now
            ).toEntity(userId).copy(syncId = UUID.randomUUID().toString(), updatedAt = now)
            val newId = transactionDao.upsert(gift)
            transactionOps += gift.copy(id = newId) to SyncOperation.CREATE
            newId
        }

        val giftedLoan = loan.copy(
            giftedAmount = plan.giftAmount,
            giftTransactionId = giftTransactionId,
            giftedAt = now,
            remainingAmount = 0L,
            status = LoanStatus.GIFTED,
            updatedAt = now,
            syncId = loan.syncId ?: UUID.randomUUID().toString()
        )
        loanDao.upsert(giftedLoan)
        return Result(giftTransactionId, transactionOps, giftedLoan)
    }
}
