package com.arzikina.ne.data.repository

import androidx.room.withTransaction
import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.local.dao.FinancialPlanDao
import com.arzikina.ne.data.local.dao.FinancialPlanItemDao
import com.arzikina.ne.data.local.dao.TransactionDao
import com.arzikina.ne.data.local.database.ArzikinaDatabase
import com.arzikina.ne.data.local.entity.FinancialPlanEntity
import com.arzikina.ne.data.local.entity.FinancialPlanItemEntity
import com.arzikina.ne.data.local.entity.TransactionEntity
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.data.remote.dto.FinancialPlanItemSyncPayload
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
 * DAO dans une seule transaction Room. [TransactionSyncEnqueuer] injecté pour la même raison (voir
 * sa KDoc de tête) : ce repository est l'un des cinq qui écrivent des transactions.
 *
 * `financial_plan_items` (étape 21) : propriétaire UNIQUE de [FinancialPlanItemEntity], même patron
 * que `BudgetRepositoryImpl`/`CategoryRepositoryImpl` — pas de classe `*SyncEnqueuer` partagée,
 * [enqueueFinancialPlanItemSync] reste privée à cette classe. [CategoryDao] injecté en plus pour
 * résoudre [FinancialPlanItemEntity.categoryId] (voir [resolveCategorySyncId]).
 */
class FinancialPlanRepositoryImpl @Inject constructor(
    private val database: ArzikinaDatabase,
    private val financialPlanDao: FinancialPlanDao,
    private val financialPlanItemDao: FinancialPlanItemDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val sessionManager: SessionManager,
    private val syncQueueEnqueuer: SyncQueueEnqueuer,
    private val transactionSyncEnqueuer: TransactionSyncEnqueuer,
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
     * rattrapage EXPLICITE de ses dépenses prévues, DÉSORMAIS ligne par ligne (étape 21, depuis que
     * `financial_plan_items` est lui-même synchronisé) plutôt que via l'ancien `UPDATE` bulk
     * `softDeleteAllForPlan` (supprimé) : chaque dépense prévue a son propre `syncId`, il faut
     * pouvoir enfiler CHACUNE individuellement — voir [FinancialPlanItemDao.getAllForPlan] (lu AVANT
     * toute suppression, pour connaître leur état exact). Un `UPDATE` ne déclenche jamais le cascade
     * SQLite `ForeignKey.CASCADE` qu'un vrai `DELETE` fournissait avant cette étape. N'affecte
     * AUCUNE transaction réelle (voir la doc de `FinancialPlanRepository.deletePlan`), inchangé sur
     * ce point précis.
     *
     * `syncId ?: UUID.randomUUID()...` : même filet de sécurité que
     * `CategoryRepositoryImpl.deleteCategory` pour une planification/dépense prévue jamais modifiée
     * depuis l'ajout de la synchronisation.
     */
    override suspend fun deletePlan(id: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val existing = financialPlanDao.getById(id, userId) ?: return@withContext
        val now = System.currentTimeMillis()
        val items = financialPlanItemDao.getAllForPlan(id, userId)

        financialPlanDao.softDeleteById(id, userId, now)
        val pendingItemOps = mutableListOf<Pair<FinancialPlanItemEntity, SyncOperation>>()
        items.forEach { item ->
            financialPlanItemDao.softDeleteById(item.id, userId, now)
            pendingItemOps += item.copy(
                syncId = item.syncId ?: UUID.randomUUID().toString(),
                deletedAt = now,
                updatedAt = now
            ) to SyncOperation.DELETE
        }

        enqueueFinancialPlanSync(
            existing.copy(syncId = existing.syncId ?: UUID.randomUUID().toString(), deletedAt = now, updatedAt = now),
            operation = SyncOperation.DELETE
        )
        // APRÈS l'enfilage du plan lui-même — [resolvePlanSyncId] utilise `getByIdIncludingDeleted`
        // (voir sa KDoc) précisément pour ce cas : le plan est déjà soft-supprimé à ce stade.
        pendingItemOps.forEach { (entity, operation) -> enqueueFinancialPlanItemSync(entity, operation) }
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

    /**
     * [FinancialPlanItemEntity.syncId]/[FinancialPlanItemEntity.version] PRÉSERVÉS d'une
     * modification à l'autre (JAMAIS réinitialisés par `toEntity`, qui les ignore volontairement,
     * voir `FinancialPlanItemMapper`) — bug réel identifié avant l'étape 21 (cette fonction
     * réinitialisait silencieusement ces colonnes à chaque sauvegarde, alors qu'elles existaient
     * déjà en base depuis l'ajout de la colonne). Même raisonnement que
     * `CategoryRepositoryImpl.saveCategory`/`savePlan` ci-dessus.
     */
    override suspend fun saveItem(item: FinancialPlanItem): Long = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val existing = if (item.id != 0L) financialPlanItemDao.getById(item.id, userId) else null
        val now = System.currentTimeMillis()
        val createdAt = existing?.createdAt ?: now

        val entity = item.copy(createdAt = createdAt, updatedAt = now).toEntity(userId).copy(
            syncId = existing?.syncId ?: UUID.randomUUID().toString(),
            deletedAt = existing?.deletedAt,
            version = existing?.version ?: 1
        )
        val generatedId = financialPlanItemDao.upsert(entity)
        val savedId = if (item.id != 0L) item.id else generatedId
        enqueueFinancialPlanItemSync(
            entity.copy(id = savedId),
            operation = if (existing == null) SyncOperation.CREATE else SyncOperation.UPDATE
        )
        savedId
    }

    /** Suppression DOUCE (étape 21 — remplace l'ancien vrai `DELETE`, incompatible avec la
     *  propagation multi-appareils ET avec la règle du projet "jamais supprimer de donnée
     *  financière") — voir `CategoryRepositoryImpl.deleteCategory` pour le raisonnement complet
     *  (même principe, y compris le filet de sécurité `syncId ?: UUID.randomUUID()...`). */
    override suspend fun deleteItem(id: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val existing = financialPlanItemDao.getById(id, userId) ?: return@withContext
        val now = System.currentTimeMillis()

        financialPlanItemDao.softDeleteById(id, userId, now)
        enqueueFinancialPlanItemSync(
            existing.copy(syncId = existing.syncId ?: UUID.randomUUID().toString(), deletedAt = now, updatedAt = now),
            operation = SyncOperation.DELETE
        )
    }

    /**
     * `financialPlanItemDao.upsert` ICI mute [FinancialPlanItemEntity.transactionId]/`actualAmount`/
     * `status` — un recalcul en dehors de tout appel direct à [saveItem], donc jamais enfilé avant
     * l'étape 21 (voir la KDoc de tête de `LoanRepositoryImpl.recordPayment`/
     * `RecurringTransactionRepositoryImpl.generateMissingOccurrences` pour le même motif de bug déjà
     * rencontré deux fois : un effet de bord local qui devient invisible aux autres appareils tant
     * qu'il n'est pas explicitement enfilé). `pendingItemOp` construit et enfilé de la même manière
     * que `pendingSyncOp` (transaction) — [syncId] généré à la volée si l'item n'avait encore jamais
     * été synchronisé (`item.syncId ?: UUID.randomUUID()...`, même filet de sécurité que [saveItem]),
     * l'opération devient alors un `CREATE` plutôt qu'un `UPDATE` (le serveur n'a jamais vu cette
     * ligne).
     */
    override suspend fun convertItemToTransaction(
        itemId: Long,
        accountId: Long,
        categoryId: Long,
        actualAmount: Long,
        date: Long,
        description: String
    ): Long = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        var pendingTransactionOp: Pair<TransactionEntity, SyncOperation>? = null
        var pendingItemOp: Pair<FinancialPlanItemEntity, SyncOperation>? = null

        val result = database.withTransaction {
            val item = financialPlanItemDao.getById(itemId, userId) ?: error("Dépense prévue introuvable.")
            check(item.transactionId == null) { "Cette dépense prévue a déjà été convertie en transaction." }
            // Étape 11 : une dépense annulée n'a plus lieu d'être honorée — garde de dernier
            // recours en plus de celle déjà posée côté UI (voir
            // `FinancialPlanItemConvertViewModel.init`/`FinancialPlanItemFormFragment.render`).
            check(item.status != PlanItemStatus.CANCELLED) { "Cette dépense prévue a été annulée." }

            val now = System.currentTimeMillis()
            val transactionEntity = Transaction(
                amount = actualAmount,
                type = TransactionType.EXPENSE,
                accountId = accountId,
                categoryId = categoryId,
                date = date,
                description = description,
                createdAt = now
            ).toEntity(userId).copy(syncId = UUID.randomUUID().toString(), updatedAt = now)
            val transactionId = transactionDao.upsert(transactionEntity)
            pendingTransactionOp = transactionEntity.copy(id = transactionId) to SyncOperation.CREATE

            val updatedItem = item.copy(
                transactionId = transactionId,
                actualAmount = actualAmount,
                status = PlanItemStatus.DONE,
                syncId = item.syncId ?: UUID.randomUUID().toString(),
                updatedAt = now
            )
            financialPlanItemDao.upsert(updatedItem)
            pendingItemOp = updatedItem to (if (item.syncId == null) SyncOperation.CREATE else SyncOperation.UPDATE)
            transactionId
        }

        pendingTransactionOp?.let { (entity, operation) -> transactionSyncEnqueuer.enqueue(entity, operation) }
        pendingItemOp?.let { (entity, operation) -> enqueueFinancialPlanItemSync(entity, operation) }
        result
    }

    private suspend fun enqueueFinancialPlanItemSync(entity: FinancialPlanItemEntity, operation: SyncOperation) {
        val payload = FinancialPlanItemSyncPayload(
            id = requireNotNull(entity.syncId) { "syncId doit être généré avant l'enfilage." },
            baseVersion = if (operation == SyncOperation.CREATE) null else entity.version,
            planSyncId = resolvePlanSyncId(entity.planId, entity.userId),
            name = entity.name,
            amount = entity.amount,
            actualAmount = entity.actualAmount,
            categorySyncId = entity.categoryId?.let { resolveCategorySyncId(it, entity.userId) },
            description = entity.description,
            plannedDate = entity.plannedDate,
            priority = entity.priority.name,
            status = entity.status.name,
            transactionSyncId = entity.transactionId?.let { resolveTransactionSyncId(it, entity.userId) },
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
        syncQueueEnqueuer.enqueue(
            entityType = "financial_plan_items",
            entitySyncId = payload.id,
            operation = operation,
            payloadJson = json.encodeToString(FinancialPlanItemSyncPayload.serializer(), payload)
        )
    }

    /** La planification parente DOIT exister (contrainte `ForeignKey.CASCADE` réelle sur `planId` —
     *  voir `FinancialPlanItemEntity` — une dépense prévue ne peut pas survivre à sa planification) :
     *  son absence serait une corruption de données, pas un cas à absorber silencieusement, d'où
     *  `error()`. `getByIdIncludingDeleted` (PAS `getById`, même raisonnement que
     *  `TransactionSyncEnqueuer.resolveAccountSyncId`, voir sa KDoc) : la planification peut avoir
     *  déjà été soft-supprimée dans la MÊME cascade que cette dépense prévue, voir [deletePlan] où
     *  l'enfilage des items a lieu APRÈS le commit de `financialPlanDao.softDeleteById`. */
    private suspend fun resolvePlanSyncId(planId: Long, userId: Long): String {
        val plan = financialPlanDao.getByIdIncludingDeleted(planId, userId)
            ?: error("Planification introuvable pour la dépense prévue (planId=$planId).")
        return resolveOrAssignSyncId(plan.syncId) { newSyncId -> financialPlanDao.upsert(plan.copy(syncId = newSyncId)) }
    }

    /** Voir la KDoc de [resolvePlanSyncId] (même raisonnement `getByIdIncludingDeleted` : aucune
     *  cascade connue ne supprime une catégorie en même temps qu'une dépense prévue aujourd'hui,
     *  mais ce résolveur reste défensif par cohérence avec `TransactionSyncEnqueuer`). */
    private suspend fun resolveCategorySyncId(categoryId: Long, userId: Long): String {
        val category = categoryDao.getByIdIncludingDeleted(categoryId, userId)
            ?: error("Catégorie introuvable pour la dépense prévue (categoryId=$categoryId).")
        return resolveOrAssignSyncId(category.syncId) { newSyncId -> categoryDao.upsert(category.copy(syncId = newSyncId)) }
    }

    /** Voir la KDoc de [resolvePlanSyncId] (même raisonnement `getByIdIncludingDeleted`) : la
     *  transaction de conversion peut être soft-supprimée dans une cascade `AccountRepositoryImpl`
     *  postérieure sans que cette dépense prévue ne soit elle-même retouchée. */
    private suspend fun resolveTransactionSyncId(transactionId: Long, userId: Long): String {
        val transaction = transactionDao.getByIdIncludingDeleted(transactionId, userId)
            ?: error("Transaction introuvable pour la dépense prévue (transactionId=$transactionId).")
        return resolveOrAssignSyncId(transaction.syncId) { newSyncId ->
            transactionDao.upsert(transaction.copy(syncId = newSyncId))
        }
    }

    /** Voir la KDoc de tête de `TransactionSyncEnqueuer.resolveOrAssignSyncId` (même filet de
     *  sécurité, dupliqué ici volontairement : un seul appelant, une classe partagée n'apporterait
     *  rien — voir la KDoc de tête de cette classe). */
    private suspend fun resolveOrAssignSyncId(existing: String?, persist: suspend (String) -> Unit): String {
        existing?.let { return it }
        val newSyncId = UUID.randomUUID().toString()
        persist(newSyncId)
        return newSyncId
    }

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
