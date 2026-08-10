package com.arzikina.ne.data.repository

import com.arzikina.ne.data.local.dao.TransactionDao
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Isolation multi-utilisateurs : voir `AccountRepositoryImpl` pour le raisonnement. */
class TransactionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
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

    override suspend fun saveTransaction(transaction: Transaction): Long = withContext(ioDispatcher) {
        val generatedId = transactionDao.upsert(transaction.toEntity(requireCurrentUserId()))
        // @Upsert ne retourne l'id généré QUE pour une insertion réelle (voir `AccountRepositoryImpl.saveAccount`).
        if (transaction.id != 0L) transaction.id else generatedId
    }

    override suspend fun deleteTransaction(id: Long) =
        withContext(ioDispatcher) { transactionDao.deleteById(id, requireCurrentUserId()) }

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
