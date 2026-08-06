package com.arzikina.ne.data.repository

import com.arzikina.ne.data.local.dao.SavingsGoalDao
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.SavingsGoal
import com.arzikina.ne.domain.repository.SavingsGoalRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SavingsGoalRepositoryImpl @Inject constructor(
    private val savingsGoalDao: SavingsGoalDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : SavingsGoalRepository {

    override fun observeSavingsGoals(): Flow<List<SavingsGoal>> =
        savingsGoalDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getSavingsGoal(id: Long): SavingsGoal? =
        withContext(ioDispatcher) { savingsGoalDao.getById(id)?.toDomain() }

    override suspend fun saveSavingsGoal(goal: SavingsGoal) =
        withContext(ioDispatcher) { savingsGoalDao.upsert(goal.toEntity()) }

    override suspend fun addContribution(id: Long, amountDelta: Long) =
        withContext(ioDispatcher) { savingsGoalDao.addContribution(id, amountDelta) }

    override suspend fun deleteSavingsGoal(id: Long) =
        withContext(ioDispatcher) { savingsGoalDao.deleteById(id) }
}
