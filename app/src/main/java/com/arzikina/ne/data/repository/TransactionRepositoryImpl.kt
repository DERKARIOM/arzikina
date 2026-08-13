package com.arzikina.ne.data.repository

import androidx.room.withTransaction
import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.local.dao.TransactionDao
import com.arzikina.ne.data.local.database.ArzikinaDatabase
import com.arzikina.ne.data.local.database.SystemCategoryResolver
import com.arzikina.ne.data.local.entity.TransactionEntity
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.FeeCategoryNames
import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.model.TransactionFee
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Isolation multi-utilisateurs : voir `AccountRepositoryImpl` pour le raisonnement.
 *
 * [database]/[categoryDao] : nécessaires depuis l'introduction des frais supplémentaires (voir
 * [TransactionFee]) — `saveTransaction`/`deleteTransaction` écrivent désormais potentiellement
 * DEUX lignes `transactions` de façon atomique (`database.withTransaction`, même principe que
 * `AccountRepositoryImpl.deleteAccount`/`LoanRepositoryImpl`), et doivent résoudre la catégorie
 * système "Frais et commissions" (voir [SystemCategoryResolver]).
 */
class TransactionRepositoryImpl @Inject constructor(
    private val database: ArzikinaDatabase,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val sessionManager: SessionManager,
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

    override suspend fun saveTransaction(transaction: Transaction, fee: TransactionFee?): Long =
        withContext(ioDispatcher) {
            val userId = requireCurrentUserId()
            database.withTransaction {
                // Ligne existante AVANT cette sauvegarde (mode édition uniquement) : seule source fiable
                // pour savoir si une transaction de frais est DÉJÀ liée, et donc s'il faut la mettre à
                // jour EN PLACE (jamais une deuxième ligne, voir la doc de `TransactionFee`) ou la créer.
                val existingFeeTransactionId = if (transaction.id != 0L) {
                    transactionDao.getById(transaction.id, userId)?.feeTransactionId
                } else {
                    null
                }

                val feeTransactionId: Long? = when {
                    fee != null -> upsertFeeTransaction(fee, transaction.date, existingFeeTransactionId, userId)
                    // Frais retirés en édition (Switch désactivé) : supprimer la ligne devenue orpheline
                    // plutôt que de laisser un pointeur mort sur la transaction principale.
                    existingFeeTransactionId != null -> {
                        transactionDao.deleteById(existingFeeTransactionId, userId)
                        null
                    }
                    else -> null
                }

                val entityToSave = transaction.toEntity(userId).copy(
                    feeTransactionId = feeTransactionId,
                    // Toujours null sur la transaction PRINCIPALE : feeType n'a de sens que sur la
                    // ligne de frais elle-même (voir Transaction.feeType), quoi que l'appelant ait fourni.
                    feeType = null
                )
                val generatedId = transactionDao.upsert(entityToSave)
                // @Upsert ne retourne l'id généré QUE pour une insertion réelle (voir `AccountRepositoryImpl.saveAccount`).
                if (transaction.id != 0L) transaction.id else generatedId
            }
        }

    /**
     * Crée la transaction de frais si [existingFeeTransactionId] est `null`, la met à jour EN
     * PLACE (même id) sinon — jamais les deux à la fois, ce qui garantit qu'une modification
     * n'applique jamais les frais deux fois (aucune ligne dupliquée possible).
     */
    private suspend fun upsertFeeTransaction(
        fee: TransactionFee,
        transactionDate: Long,
        existingFeeTransactionId: Long?,
        userId: Long
    ): Long {
        val feesCategory = SystemCategoryResolver.resolve(categoryDao, FeeCategoryNames.FEES, userId)
        // Préserve la date de création d'origine en cas de mise à jour (comportement standard
        // d'une édition, voir TransactionFormViewModel.init pour la même logique côté transaction
        // principale) plutôt que de la réinitialiser à chaque modification des frais.
        val existingCreatedAt = existingFeeTransactionId?.let { transactionDao.getById(it, userId)?.createdAt }
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
            createdAt = existingCreatedAt ?: System.currentTimeMillis(),
            feeTransactionId = null,
            feeType = fee.type
        )
        val generatedId = transactionDao.upsert(feeEntity)
        return existingFeeTransactionId ?: generatedId
    }

    override suspend fun deleteTransaction(id: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        database.withTransaction {
            // Cascade applicative (pas de FK sur feeTransactionId, voir TransactionEntity) : supprimer
            // la transaction de frais liée AVANT la principale pour ne jamais laisser de ligne orpheline.
            transactionDao.getById(id, userId)?.feeTransactionId?.let { feeId ->
                transactionDao.deleteById(feeId, userId)
            }
            transactionDao.deleteById(id, userId)
        }
    }

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
