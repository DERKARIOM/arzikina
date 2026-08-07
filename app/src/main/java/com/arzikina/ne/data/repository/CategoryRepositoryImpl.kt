package com.arzikina.ne.data.repository

import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.CategoryRepository
import com.arzikina.ne.domain.repository.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Isolation multi-utilisateurs : voir `AccountRepositoryImpl` pour le raisonnement. */
class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
    private val sessionManager: SessionManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CategoryRepository {

    override fun observeCategories(): Flow<List<Category>> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                categoryDao.observeAllForUser(userId).map { entities -> entities.map { it.toDomain() } }
            }
        }

    override fun observeCategoriesByType(type: TransactionType): Flow<List<Category>> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                categoryDao.observeByTypeForUser(type, userId).map { entities -> entities.map { it.toDomain() } }
            }
        }

    override suspend fun getCategory(id: Long): Category? =
        withContext(ioDispatcher) { categoryDao.getById(id, requireCurrentUserId())?.toDomain() }

    override suspend fun saveCategory(category: Category) =
        withContext(ioDispatcher) { categoryDao.upsert(category.toEntity(requireCurrentUserId())) }

    override suspend fun deleteCategory(id: Long) =
        withContext(ioDispatcher) { categoryDao.deleteById(id, requireCurrentUserId()) }

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
