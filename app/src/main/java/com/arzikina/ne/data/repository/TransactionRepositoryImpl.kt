package com.arzikina.ne.data.repository

import com.arzikina.ne.data.local.dao.TransactionDao
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TransactionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : TransactionRepository {

    override fun observeTransactions(): Flow<List<Transaction>> =
        transactionDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getTransaction(id: Long): Transaction? =
        withContext(ioDispatcher) { transactionDao.getById(id)?.toDomain() }

    override suspend fun saveTransaction(transaction: Transaction) =
        withContext(ioDispatcher) { transactionDao.upsert(transaction.toEntity()) }

    override suspend fun deleteTransaction(id: Long) =
        withContext(ioDispatcher) { transactionDao.deleteById(id) }
}
