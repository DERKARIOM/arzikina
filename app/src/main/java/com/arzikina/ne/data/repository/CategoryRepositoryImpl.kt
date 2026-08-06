package com.arzikina.ne.data.repository

import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.CategoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CategoryRepository {

    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeCategoriesByType(type: TransactionType): Flow<List<Category>> =
        categoryDao.observeByType(type).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getCategory(id: Long): Category? =
        withContext(ioDispatcher) { categoryDao.getById(id)?.toDomain() }

    override suspend fun saveCategory(category: Category) =
        withContext(ioDispatcher) { categoryDao.upsert(category.toEntity()) }

    override suspend fun deleteCategory(id: Long) =
        withContext(ioDispatcher) { categoryDao.deleteById(id) }
}
