package com.arzikina.ne.data.repository

import com.arzikina.ne.data.local.dao.BudgetDao
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.Budget
import com.arzikina.ne.domain.repository.BudgetRepository
import com.arzikina.ne.domain.repository.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Isolation multi-utilisateurs : voir `AccountRepositoryImpl` pour le raisonnement. */
class BudgetRepositoryImpl @Inject constructor(
    private val budgetDao: BudgetDao,
    private val sessionManager: SessionManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : BudgetRepository {

    override fun observeBudgets(): Flow<List<Budget>> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                budgetDao.observeAllForUser(userId).map { entities -> entities.map { it.toDomain() } }
            }
        }

    override suspend fun getBudget(id: Long): Budget? =
        withContext(ioDispatcher) { budgetDao.getById(id, requireCurrentUserId())?.toDomain() }

    override suspend fun saveBudget(budget: Budget) =
        withContext(ioDispatcher) { budgetDao.upsert(budget.toEntity(requireCurrentUserId())) }

    override suspend fun deleteBudget(id: Long) =
        withContext(ioDispatcher) { budgetDao.deleteById(id, requireCurrentUserId()) }

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
