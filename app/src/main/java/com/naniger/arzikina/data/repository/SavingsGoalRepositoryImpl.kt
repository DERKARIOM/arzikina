package com.naniger.arzikina.data.repository

import com.naniger.arzikina.data.local.dao.SavingsGoalDao
import com.naniger.arzikina.data.local.entity.SavingsGoalEntity
import com.naniger.arzikina.data.mapper.toDomain
import com.naniger.arzikina.data.mapper.toEntity
import com.naniger.arzikina.data.remote.dto.SavingsGoalSyncPayload
import com.naniger.arzikina.di.IoDispatcher
import com.naniger.arzikina.domain.model.SavingsGoal
import com.naniger.arzikina.domain.model.SyncOperation
import com.naniger.arzikina.domain.repository.SavingsGoalRepository
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
 * Isolation multi-utilisateurs : voir `AccountRepositoryImpl` pour le raisonnement.
 *
 * Câblage sync (`syncId`/`version`/enfilage) : même schéma que [CategoryRepositoryImpl] — voir sa
 * KDoc de tête pour le raisonnement complet (2ᵉ entité câblée, voir docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md).
 * Seule différence réelle : [addContribution] est une écriture PARTIELLE (SQL brut, pas de
 * relecture-modification-écriture complète) — voir sa doc pour comment elle reste malgré tout
 * synchronisée comme n'importe quelle autre mutation.
 */
class SavingsGoalRepositoryImpl @Inject constructor(
    private val savingsGoalDao: SavingsGoalDao,
    private val sessionManager: SessionManager,
    private val syncQueueEnqueuer: SyncQueueEnqueuer,
    private val json: Json,
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

    override suspend fun saveSavingsGoal(goal: SavingsGoal) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val existing = if (goal.id != 0L) savingsGoalDao.getById(goal.id, userId) else null
        val now = System.currentTimeMillis()

        val entity = goal.toEntity(userId).copy(
            syncId = existing?.syncId ?: UUID.randomUUID().toString(),
            createdAt = existing?.createdAt ?: goal.createdAt,
            updatedAt = now,
            deletedAt = existing?.deletedAt,
            version = existing?.version ?: 1
        )
        savingsGoalDao.upsert(entity)
        enqueueSavingsGoalSync(entity, operation = if (existing == null) SyncOperation.CREATE else SyncOperation.UPDATE)
    }

    /** Voir la doc de classe : [SavingsGoalDao.addContribution] fait progresser `updatedAt` en même
     *  temps que `currentAmount` (une seule requête SQL atomique, voir sa KDoc) — cette méthode
     *  relit ensuite la ligne pour construire l'entrée `sync_queue`, [ensureSyncId] comblant le seul
     *  cas où elle n'existait pas encore. */
    override suspend fun addContribution(id: Long, amountDelta: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val now = System.currentTimeMillis()

        savingsGoalDao.addContribution(id, amountDelta, userId, now)
        val updated = savingsGoalDao.getById(id, userId) ?: return@withContext
        val entity = ensureSyncId(updated)
        enqueueSavingsGoalSync(entity, operation = SyncOperation.UPDATE)
    }

    /**
     * Suppression DOUCE — voir `CategoryRepositoryImpl.deleteCategory` pour le raisonnement complet
     * (silencieux si [id] introuvable, `syncId` généré en mémoire seulement si jamais assigné, sans
     * le persister — la ligne est supprimée, inutile de le relire plus tard).
     */
    override suspend fun deleteSavingsGoal(id: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val existing = savingsGoalDao.getById(id, userId) ?: return@withContext
        val now = System.currentTimeMillis()

        savingsGoalDao.softDeleteById(id, userId, now)
        enqueueSavingsGoalSync(
            existing.copy(syncId = existing.syncId ?: UUID.randomUUID().toString(), deletedAt = now, updatedAt = now),
            operation = SyncOperation.DELETE
        )
    }

    /** Génère et PERSISTE un `syncId` si la ligne n'en a encore aucun (voir [addContribution]) —
     *  contrairement à [deleteSavingsGoal], cette ligne reste active et sera relue plus tard : le
     *  `syncId` généré doit donc être écrit en base, pas seulement tenu en mémoire pour ce payload. */
    private suspend fun ensureSyncId(entity: SavingsGoalEntity): SavingsGoalEntity {
        if (entity.syncId != null) return entity
        val withSyncId = entity.copy(syncId = UUID.randomUUID().toString())
        savingsGoalDao.upsert(withSyncId)
        return withSyncId
    }

    private suspend fun enqueueSavingsGoalSync(entity: SavingsGoalEntity, operation: SyncOperation) {
        val payload = SavingsGoalSyncPayload(
            id = requireNotNull(entity.syncId) { "syncId doit être généré avant l'enfilage." },
            baseVersion = if (operation == SyncOperation.CREATE) null else entity.version,
            name = entity.name,
            targetAmount = entity.targetAmount,
            currentAmount = entity.currentAmount,
            currencyCode = entity.currencyCode,
            deadline = entity.deadline,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
        syncQueueEnqueuer.enqueue(
            entityType = "savings_goals",
            entitySyncId = payload.id,
            operation = operation,
            payloadJson = json.encodeToString(SavingsGoalSyncPayload.serializer(), payload)
        )
    }

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
