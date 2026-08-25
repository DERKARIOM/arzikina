package com.arzikina.ne.data.repository

import androidx.room.withTransaction
import com.arzikina.ne.data.local.dao.FinancialPlanDao
import com.arzikina.ne.data.local.dao.FinancialPlanItemDao
import com.arzikina.ne.data.local.dao.TransactionDao
import com.arzikina.ne.data.local.database.ArzikinaDatabase
import com.arzikina.ne.data.local.entity.FinancialPlanEntity
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.data.remote.dto.FinancialPlanSyncPayload
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.FinancialPlan
import com.arzikina.ne.domain.model.FinancialPlanItem
import com.arzikina.ne.domain.model.PlanItemStatus
import com.arzikina.ne.domain.model.SyncOperation
import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.FinancialPlanRepository
import com.arzikina.ne.domain.repository.SessionManager
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
 * Implémentation Room de [FinancialPlanRepository].
 *
 * Isolation multi-utilisateurs : voir `AccountRepositoryImpl` pour le raisonnement.
 *
 * [convertItemToTransaction] EXCEPTÉE (voir sa doc) : toutes les AUTRES écritures ne touchent
 * jamais `TransactionDao` ni `ArzikinaDatabase.withTransaction` — une planification et ses
 * dépenses prévues restent des données PUREMENT PRÉVISIONNELLES tant que l'utilisateur ne les
 * convertit pas explicitement. Pas besoin de transaction Room multi-tables pour un simple CRUD sur
 * une seule table à la fois (la suppression en cascade des dépenses prévues d'une planification
 * est déjà garantie par SQLite, voir `FinancialPlanItemEntity`).
 *
 * `createdAt`/`updatedAt` sont TOUJOURS recalculés ici (jamais ceux fournis par l'appelant) — même
 * principe que `LoanRepositoryImpl.saveLoan`.
 *
 * Dépend directement de [TransactionDao] (pas de `TransactionRepository`) pour
 * [convertItemToTransaction] — même raisonnement que `LoanRepositoryImpl` (voir sa doc) : un
 * repository ne doit pas dépendre d'un autre repository pour rester libre de composer plusieurs
 * DAO dans une seule transaction Room.
 */
class FinancialPlanRepositoryImpl @Inject constructor(
    private val database: ArzikinaDatabase,
    private val financialPlanDao: FinancialPlanDao,
    private val financialPlanItemDao: FinancialPlanItemDao,
    private val transactionDao: TransactionDao,
    private val sessionManager: SessionManager,
    private val syncQueueEnqueuer: SyncQueueEnqueuer,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : FinancialPlanRepository {

    override fun observePlans(): Flow<List<FinancialPlan>> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                financialPlanDao.observeAllForUser(userId).map { entities -> entities.map { it.toDomain() } }
            }
        }

    override suspend fun getPlan(id: Long): FinancialPlan? =
        withContext(ioDispatcher) { financialPlanDao.getById(id, requireCurrentUserId())?.toDomain() }

    override fun observeItems(planId: Long): Flow<List<FinancialPlanItem>> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                financialPlanItemDao.observeForPlan(planId, userId).map { entities -> entities.map { it.toDomain() } }
            }
        }

    override suspend fun getItem(id: Long): FinancialPlanItem? =
        withContext(ioDispatcher) { financialPlanItemDao.getById(id, requireCurrentUserId())?.toDomain() }

    override fun observeAllItems(): Flow<List<FinancialPlanItem>> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                financialPlanItemDao.observeAllForUser(userId).map { entities -> entities.map { it.toDomain() } }
            }
        }

    /**
     * [FinancialPlanEntity.syncId]/[FinancialPlanEntity.version] PRÉSERVÉS d'une modification à
     * l'autre (jamais réinitialisés par `toEntity`, qui les ignore volontairement) — même
     * raisonnement que `CategoryRepositoryImpl.saveCategory` (voir sa KDoc pour le détail complet).
     */
    override suspend fun savePlan(plan: FinancialPlan): Long = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val existing = if (plan.id != 0L) financialPlanDao.getById(plan.id, userId) else null
        val now = System.currentTimeMillis()
        val createdAt = existing?.createdAt ?: now

        val entity = plan.copy(createdAt = createdAt, updatedAt = now).toEntity(userId).copy(
            syncId = existing?.syncId ?: UUID.randomUUID().toString(),
            deletedAt = existing?.deletedAt,
            version = existing?.version ?: 1
        )
        val generatedId = financialPlanDao.upsert(entity)
        val savedId = if (plan.id != 0L) plan.id else generatedId
        enqueueFinancialPlanSync(
            entity.copy(id = savedId),
            operation = if (existing == null) SyncOperation.CREATE else SyncOperation.UPDATE
        )
        savedId
    }

    /**
     * Suppression DOUCE de la planification (voir `FinancialPlanDao.softDeleteById`) — PUIS
     * rattrapage EXPLICITE de ses dépenses prévues via
     * `FinancialPlanItemDao.softDeleteAllForPlan` : un `UPDATE` ne déclenche jamais le cascade
     * SQLite `ForeignKey.CASCADE` qu'un vrai `DELETE` fournissait avant cette étape (voir la KDoc de
     * `FinancialPlanItemDao.softDeleteAllForPlan`). N'affecte AUCUNE transaction réelle (voir la
     * doc de `FinancialPlanRepository.deletePlan`), inchangé sur ce point précis.
     *
     * `syncId ?: UUID.randomUUID()...` : même filet de sécurité que
     * `CategoryRepositoryImpl.deleteCategory` pour une planification jamais modifiée depuis
     * l'ajout de la synchronisation.
     */
    override suspend fun deletePlan(id: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val existing = financialPlanDao.getById(id, userId) ?: return@withContext
        val now = System.currentTimeMillis()

        financialPlanDao.softDeleteById(id, userId, now)
        financialPlanItemDao.softDeleteAllForPlan(id, userId, now)
        enqueueFinancialPlanSync(
            existing.copy(syncId = existing.syncId ?: UUID.randomUUID().toString(), deletedAt = now, updatedAt = now),
            operation = SyncOperation.DELETE
        )
    }

    private suspend fun enqueueFinancialPlanSync(entity: FinancialPlanEntity, operation: SyncOperation) {
        val payload = FinancialPlanSyncPayload(
            id = requireNotNull(entity.syncId) { "syncId doit être généré avant l'enfilage." },
            baseVersion = if (operation == SyncOperation.CREATE) null else entity.version,
            name = entity.name,
            description = entity.description,
            availableAmount = entity.availableAmount,
            targetAmount = entity.targetAmount,
            periodType = entity.periodType.name,
            startDate = entity.startDate,
            endDate = entity.endDate,
            icon = entity.icon.name,
            colorArgb = entity.colorArgb,
            status = entity.status.name,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
        syncQueueEnqueuer.enqueue(
            entityType = "financial_plans",
            entitySyncId = payload.id,
            operation = operation,
            payloadJson = json.encodeToString(FinancialPlanSyncPayload.serializer(), payload)
        )
    }

    override suspend fun saveItem(item: FinancialPlanItem): Long = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val now = System.currentTimeMillis()
        val createdAt = if (item.id == 0L) {
            now
        } else {
            financialPlanItemDao.getById(item.id, userId)?.createdAt ?: now
        }
        financialPlanItemDao.upsert(item.copy(createdAt = createdAt, updatedAt = now).toEntity(userId))
    }

    override suspend fun deleteItem(id: Long) = withContext(ioDispatcher) {
        financialPlanItemDao.deleteById(id, requireCurrentUserId())
    }

    override suspend fun convertItemToTransaction(
        itemId: Long,
        accountId: Long,
        categoryId: Long,
        actualAmount: Long,
        date: Long,
        description: String
    ): Long = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        database.withTransaction {
            val item = financialPlanItemDao.getById(itemId, userId) ?: error("Dépense prévue introuvable.")
            check(item.transactionId == null) { "Cette dépense prévue a déjà été convertie en transaction." }
            // Étape 11 : une dépense annulée n'a plus lieu d'être honorée — garde de dernier
            // recours en plus de celle déjà posée côté UI (voir
            // `FinancialPlanItemConvertViewModel.init`/`FinancialPlanItemFormFragment.render`).
            check(item.status != PlanItemStatus.CANCELLED) { "Cette dépense prévue a été annulée." }

            val now = System.currentTimeMillis()
            val transactionId = transactionDao.upsert(
                Transaction(
                    amount = actualAmount,
                    type = TransactionType.EXPENSE,
                    accountId = accountId,
                    categoryId = categoryId,
                    date = date,
                    description = description,
                    createdAt = now
                ).toEntity(userId)
            )
            financialPlanItemDao.upsert(
                item.copy(
                    transactionId = transactionId,
                    actualAmount = actualAmount,
                    status = PlanItemStatus.DONE,
                    updatedAt = now
                )
            )
            transactionId
        }
    }

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
