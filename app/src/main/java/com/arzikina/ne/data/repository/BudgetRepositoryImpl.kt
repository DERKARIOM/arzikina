package com.arzikina.ne.data.repository

import com.arzikina.ne.data.local.dao.BudgetDao
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.Budget
import com.arzikina.ne.domain.repository.BudgetRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class BudgetRepositoryImpl @Inject constructor(
    private val budgetDao: BudgetDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : BudgetRepository {

    override fun observeBudgets(): Flow<List<Budget>> =
        budgetDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getBudget(id: Long): Budget? =
        withContext(ioDispatcher) { budgetDao.getById(id)?.toDomain() }

    override suspend fun getBudgetForCategory(categoryId: Long): Budget? =
        withContext(ioDispatcher) { budgetDao.getByCategoryId(categoryId)?.toDomain() }

    override suspend fun saveBudget(budget: Budget) =
        withContext(ioDispatcher) { budgetDao.upsert(budget.toEntity()) }

    override suspend fun deleteBudget(id: Long) =
        withContext(ioDispatcher) { budgetDao.deleteById(id) }
}
