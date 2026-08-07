package com.arzikina.ne.data.repository

import com.arzikina.ne.data.local.dao.SavingsGoalDao
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.SavingsGoal
import com.arzikina.ne.domain.repository.SavingsGoalRepository
import com.arzikina.ne.domain.repository.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Isolation multi-utilisateurs : voir `AccountRepositoryImpl` pour le raisonnement. */
class SavingsGoalRepositoryImpl @Inject constructor(
    private val savingsGoalDao: SavingsGoalDao,
    private val sessionManager: SessionManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : SavingsGoalRepository {

    override fun observeSavingsGoals(): Flow<List<SavingsGoal>> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                savingsGoalDao.observeAllForUser(userId).map { entities -> entities.map { it.toDomain() } }
            }
        }

    override suspend fun getSavingsGoal(id: Long): SavingsGoal? =
        withContext(ioDispatcher) { savingsGoalDao.getById(id, requireCurrentUserId())?.toDomain() }

    override suspend fun saveSavingsGoal(goal: SavingsGoal) =
        withContext(ioDispatcher) { savingsGoalDao.upsert(goal.toEntity(requireCurrentUserId())) }

    override suspend fun addContribution(id: Long, amountDelta: Long) =
        withContext(ioDispatcher) { savingsGoalDao.addContribution(id, amountDelta, requireCurrentUserId()) }

    override suspend fun deleteSavingsGoal(id: Long) =
        withContext(ioDispatcher) { savingsGoalDao.deleteById(id, requireCurrentUserId()) }

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
