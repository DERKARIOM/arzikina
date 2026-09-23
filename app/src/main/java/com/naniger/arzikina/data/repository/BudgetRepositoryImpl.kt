package com.naniger.arzikina.data.repository

import com.naniger.arzikina.data.local.dao.BudgetDao
import com.naniger.arzikina.data.local.dao.CategoryDao
import com.naniger.arzikina.data.local.entity.BudgetEntity
import com.naniger.arzikina.data.mapper.toDomain
import com.naniger.arzikina.data.mapper.toEntity
import com.naniger.arzikina.data.remote.dto.BudgetSyncPayload
import com.naniger.arzikina.di.IoDispatcher
import com.naniger.arzikina.domain.model.Budget
import com.naniger.arzikina.domain.model.SyncOperation
import com.naniger.arzikina.domain.repository.BudgetRepository
import com.naniger.arzikina.domain.repository.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

/**
 * Isolation multi-utilisateurs : voir `AccountRepositoryImpl` pour le raisonnement. Câblage sync
 * (étape 18) : même patron que `CategoryRepositoryImpl`/`AccountRepositoryImpl` (propriétaire
 * UNIQUE de `BudgetEntity`, contrairement à `Transaction` — pas de classe `*SyncEnqueuer` partagée
 * nécessaire, [enqueueBudgetSync] reste privée à cette classe).
 */
class BudgetRepositoryImpl @Inject constructor(
    private val budgetDao: BudgetDao,
    private val categoryDao: CategoryDao,
    private val sessionManager: SessionManager,
    private val syncQueueEnqueuer: SyncQueueEnqueuer,
    private val json: Json,
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

    /**
     * Enfile une entrée `sync_queue` (CREATE/UPDATE) après l'écriture Room habituelle — voir
     * `CategoryRepositoryImpl.saveCategory` pour le raisonnement complet (préservation
     * `syncId`/`version`/`deletedAt`, même principe).
     */
    override suspend fun saveBudget(budget: Budget) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val existing = if (budget.id != 0L) budgetDao.getById(budget.id, userId) else null
        val now = System.currentTimeMillis()

        val entity = budget.toEntity(userId).copy(
            syncId = existing?.syncId ?: UUID.randomUUID().toString(),
            updatedAt = now,
            deletedAt = existing?.deletedAt,
            version = existing?.version ?: 1
        )
        budgetDao.upsert(entity)
        enqueueBudgetSync(entity, operation = if (existing == null) SyncOperation.CREATE else SyncOperation.UPDATE)
    }

    /** Suppression DOUCE — voir `CategoryRepositoryImpl.deleteCategory` pour le raisonnement complet
     *  (même principe, y compris le filet de sécurité `syncId ?: UUID.randomUUID()...`). */
    override suspend fun deleteBudget(id: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val existing = budgetDao.getById(id, userId) ?: return@withContext
        val now = System.currentTimeMillis()

        budgetDao.softDeleteById(id, userId, now)
        enqueueBudgetSync(
            existing.copy(syncId = existing.syncId ?: UUID.randomUUID().toString(), deletedAt = now, updatedAt = now),
            operation = SyncOperation.DELETE
        )
    }

    private suspend fun enqueueBudgetSync(entity: BudgetEntity, operation: SyncOperation) {
        val payload = BudgetSyncPayload(
            id = requireNotNull(entity.syncId) { "syncId doit être généré avant l'enfilage." },
            baseVersion = if (operation == SyncOperation.CREATE) null else entity.version,
            categorySyncId = resolveCategorySyncId(entity.categoryId, entity.userId),
            period = entity.period.name,
            limitAmount = entity.limitAmount,
            currencyCode = entity.currencyCode,
            startDate = entity.startDate,
            endDate = entity.endDate,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
        syncQueueEnqueuer.enqueue(
            entityType = "budgets",
            entitySyncId = payload.id,
            operation = operation,
            payloadJson = json.encodeToString(BudgetSyncPayload.serializer(), payload)
        )
    }

    /**
     * Résolution `id` local → `syncId` — voir la KDoc de tête de `TransactionSyncEnqueuer` pour le
     * raisonnement complet sur cette classe de champs (même filet de sécurité
     * `resolveOrAssignSyncId`, dupliqué ici volontairement : un seul appelant, une classe partagée
     * n'apporterait rien — voir la doc de tête de cette classe).
     */
    private suspend fun resolveCategorySyncId(categoryId: Long, userId: Long): String {
        val category = categoryDao.getById(categoryId, userId)
            ?: error("Catégorie introuvable pour le budget (categoryId=$categoryId).")
        category.syncId?.let { return it }
        val newSyncId = UUID.randomUUID().toString()
        categoryDao.upsert(category.copy(syncId = newSyncId))
        return newSyncId
    }

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
