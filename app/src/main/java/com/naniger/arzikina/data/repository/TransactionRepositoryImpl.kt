package com.naniger.arzikina.data.repository

import androidx.room.withTransaction
import com.naniger.arzikina.data.local.dao.CategoryDao
import com.naniger.arzikina.data.local.dao.TransactionDao
import com.naniger.arzikina.data.local.database.ArzikinaDatabase
import com.naniger.arzikina.data.local.database.SystemCategoryResolver
import com.naniger.arzikina.data.local.entity.TransactionEntity
import com.naniger.arzikina.data.mapper.toDomain
import com.naniger.arzikina.data.mapper.toEntity
import com.naniger.arzikina.di.IoDispatcher
import com.naniger.arzikina.domain.model.FeeCategoryNames
import com.naniger.arzikina.domain.model.SyncOperation
import com.naniger.arzikina.domain.model.Transaction
import com.naniger.arzikina.domain.model.TransactionFee
import com.naniger.arzikina.domain.model.TransactionType
import com.naniger.arzikina.domain.repository.SessionManager
import com.naniger.arzikina.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * Isolation multi-utilisateurs : voir `AccountRepositoryImpl` pour le raisonnement.
 *
 * [database]/[categoryDao] : nécessaires depuis l'introduction des frais supplémentaires (voir
 * [TransactionFee]) — `saveTransaction`/`deleteTransaction` écrivent désormais potentiellement
 * DEUX lignes `transactions` de façon atomique (`database.withTransaction`, même principe que
 * `AccountRepositoryImpl.deleteAccount`/`LoanRepositoryImpl`), et doivent résoudre la catégorie
 * système "Frais et commissions" (voir [SystemCategoryResolver]).
 *
 * [transactionSyncEnqueuer] : PAS de fonction `enqueueTransactionSync` privée ici, contrairement
 * aux autres repositories câblés (`CategoryRepositoryImpl`, etc.) — voir la KDoc de tête de
 * [TransactionSyncEnqueuer] : `Transaction` est écrite depuis 5 repositories, cette classe partagée
 * évite de dupliquer la construction du payload. Les appels à
 * [TransactionSyncEnqueuer.enqueue] ont lieu APRÈS le `database.withTransaction` (jamais dedans,
 * même principe que `PersonRepositoryImpl.deletePerson`) : un échec réseau/de sérialisation ne doit
 * jamais faire annuler une écriture locale déjà validée.
 */
class TransactionRepositoryImpl @Inject constructor(
    private val database: ArzikinaDatabase,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val sessionManager: SessionManager,
    private val transactionSyncEnqueuer: TransactionSyncEnqueuer,
    private val categorySyncEnqueuer: CategorySyncEnqueuer,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : TransactionRepository {

    override fun observeTransactions(): Flow<List<Transaction>> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                transactionDao.observeAllForUser(userId).map { entities -> entities.map { it.toDomain() } }
            }
        }

    override suspend fun getTransaction(id: Long): Transaction? =
        withContext(ioDispatcher) { transactionDao.getById(id, requireCurrentUserId())?.toDomain() }

    override suspend fun findByReceiptId(receiptId: Long): Transaction? =
        withContext(ioDispatcher) {
            transactionDao.findByReceiptId(receiptId, requireCurrentUserId())?.toDomain()
        }

    override fun observeReceiptIdsWithTransaction(): Flow<Set<Long>> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptySet())
            } else {
                transactionDao.observeReceiptIdsWithTransaction(userId).map { it.toSet() }
            }
        }

    /**
     * [pendingSyncOps] : accumule les (entité, opération) à enfiler APRÈS le `database.withTransaction`
     * ci-dessous (voir la KDoc de tête de cette classe) — jusqu'à DEUX lignes concernées par un seul
     * appel (transaction principale + transaction de frais), chacune pouvant avoir sa PROPRE
     * opération (une transaction de frais retirée en édition est un `DELETE`, même quand la
     * transaction principale elle-même n'est qu'une `UPDATE`).
     */
    override suspend fun saveTransaction(transaction: Transaction, fee: TransactionFee?): Long =
        withContext(ioDispatcher) {
            val userId = requireCurrentUserId()
            val pendingSyncOps = mutableListOf<Pair<TransactionEntity, SyncOperation>>()

            val savedId = database.withTransaction {
                // Ligne existante AVANT cette sauvegarde (mode édition uniquement) : seule source fiable
                // pour savoir si une transaction de frais est DÉJÀ liée, et donc s'il faut la mettre à
                // jour EN PLACE (jamais une deuxième ligne, voir la doc de `TransactionFee`) ou la créer.
                val existing = if (transaction.id != 0L) transactionDao.getById(transaction.id, userId) else null

                val feeTransactionId: Long? = when {
                    fee != null -> {
                        val (feeEntity, feeOperation) =
                            upsertFeeTransaction(fee, transaction.date, existing?.feeTransactionId, userId)
                        pendingSyncOps += feeEntity to feeOperation
                        feeEntity.id
                    }
                    // Frais retirés en édition (Switch désactivé) : supprimer la ligne devenue orpheline
                    // (suppression DOUCE désormais, voir `TransactionDao.softDeleteById`) plutôt que de
                    // laisser un pointeur mort sur la transaction principale.
                    existing?.feeTransactionId != null -> {
                        val feeEntity = transactionDao.getById(existing.feeTransactionId, userId)
                        val deletionNow = System.currentTimeMillis()
                        transactionDao.softDeleteById(existing.feeTransactionId, userId, deletionNow)
                        if (feeEntity != null) {
                            pendingSyncOps += feeEntity.copy(
                                syncId = feeEntity.syncId ?: UUID.randomUUID().toString(),
                                deletedAt = deletionNow,
                                updatedAt = deletionNow
                            ) to SyncOperation.DELETE
                        }
                        null
                    }
                    else -> null
                }

                val now = System.currentTimeMillis()
                val entityToSave = transaction.toEntity(userId).copy(
                    feeTransactionId = feeTransactionId,
                    // Toujours null sur la transaction PRINCIPALE : feeType n'a de sens que sur la
                    // ligne de frais elle-même (voir Transaction.feeType), quoi que l'appelant ait fourni.
                    feeType = null,
                    // syncId/version PRÉSERVÉS d'une modification à l'autre — même raisonnement que
                    // `AccountRepositoryImpl.saveAccount` (voir sa KDoc).
                    syncId = existing?.syncId ?: UUID.randomUUID().toString(),
                    updatedAt = now,
                    deletedAt = existing?.deletedAt,
                    version = existing?.version ?: 1
                )
                val generatedId = transactionDao.upsert(entityToSave)
                // @Upsert ne retourne l'id généré QUE pour une insertion réelle (voir `AccountRepositoryImpl.saveAccount`).
                val finalId = if (transaction.id != 0L) transaction.id else generatedId
                pendingSyncOps += entityToSave.copy(id = finalId) to
                    (if (existing == null) SyncOperation.CREATE else SyncOperation.UPDATE)
                finalId
            }

            pendingSyncOps.forEach { (entity, operation) -> transactionSyncEnqueuer.enqueue(entity, operation) }
            savedId
        }

    /**
     * Crée la transaction de frais si [existingFeeTransactionId] est `null`, la met à jour EN
     * PLACE (même id) sinon — jamais les deux à la fois, ce qui garantit qu'une modification
     * n'applique jamais les frais deux fois (aucune ligne dupliquée possible). Retourne l'entité
     * FINALE (avec son id résolu) et l'opération de synchronisation correspondante, pour que
     * l'appelant les enfile après la transaction Room (voir la KDoc de tête de cette classe).
     */
    private suspend fun upsertFeeTransaction(
        fee: TransactionFee,
        transactionDate: Long,
        existingFeeTransactionId: Long?,
        userId: Long
    ): Pair<TransactionEntity, SyncOperation> {
        val feesCategory = SystemCategoryResolver.resolve(categoryDao, categorySyncEnqueuer, FeeCategoryNames.FEES, userId)
        // Préserve la date de création d'origine ET l'état de synchronisation en cas de mise à jour
        // (comportement standard d'une édition, voir TransactionFormViewModel.init pour la même
        // logique côté transaction principale) plutôt que de les réinitialiser à chaque modification
        // des frais.
        val existingFeeEntity = existingFeeTransactionId?.let { transactionDao.getById(it, userId) }
        val now = System.currentTimeMillis()
        val feeEntity = TransactionEntity(
            id = existingFeeTransactionId ?: 0L,
            userId = userId,
            amount = fee.amount,
            type = TransactionType.EXPENSE,
            accountId = fee.accountId,
            transferAccountId = null,
            categoryId = feesCategory.id,
            date = transactionDate,
            description = fee.description.trim(),
            receiptPhotoUri = null,
            latitude = null,
            longitude = null,
            paymentMethod = null,
            createdAt = existingFeeEntity?.createdAt ?: now,
            feeTransactionId = null,
            feeType = fee.type,
            syncId = existingFeeEntity?.syncId ?: UUID.randomUUID().toString(),
            updatedAt = now,
            deletedAt = existingFeeEntity?.deletedAt,
            version = existingFeeEntity?.version ?: 1
        )
        val generatedId = transactionDao.upsert(feeEntity)
        val finalId = existingFeeTransactionId ?: generatedId
        val operation = if (existingFeeEntity == null) SyncOperation.CREATE else SyncOperation.UPDATE
        return feeEntity.copy(id = finalId) to operation
    }

    override suspend fun deleteTransaction(id: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val pendingSyncOps = mutableListOf<Pair<TransactionEntity, SyncOperation>>()
        val now = System.currentTimeMillis()

        database.withTransaction {
            val existing = transactionDao.getById(id, userId) ?: return@withTransaction
            // Cascade applicative (pas de FK sur feeTransactionId, voir TransactionEntity) : supprimer
            // (désormais DOUCEMENT) la transaction de frais liée AVANT la principale, pour ne jamais
            // laisser de ligne orpheline visible.
            existing.feeTransactionId?.let { feeId ->
                val feeEntity = transactionDao.getById(feeId, userId)
                transactionDao.softDeleteById(feeId, userId, now)
                if (feeEntity != null) {
                    pendingSyncOps += feeEntity.copy(
                        syncId = feeEntity.syncId ?: UUID.randomUUID().toString(),
                        deletedAt = now,
                        updatedAt = now
                    ) to SyncOperation.DELETE
                }
            }
            transactionDao.softDeleteById(id, userId, now)
            pendingSyncOps += existing.copy(
                syncId = existing.syncId ?: UUID.randomUUID().toString(),
                deletedAt = now,
                updatedAt = now
            ) to SyncOperation.DELETE
        }

        pendingSyncOps.forEach { (entity, operation) -> transactionSyncEnqueuer.enqueue(entity, operation) }
    }

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
