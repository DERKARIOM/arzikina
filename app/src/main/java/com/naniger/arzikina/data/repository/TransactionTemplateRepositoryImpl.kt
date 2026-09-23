package com.naniger.arzikina.data.repository

import com.naniger.arzikina.data.local.dao.AccountDao
import com.naniger.arzikina.data.local.dao.CategoryDao
import com.naniger.arzikina.data.local.dao.TransactionTemplateDao
import com.naniger.arzikina.data.local.entity.TransactionTemplateEntity
import com.naniger.arzikina.data.mapper.toDomain
import com.naniger.arzikina.data.mapper.toEntity
import com.naniger.arzikina.data.remote.dto.TransactionTemplateSyncPayload
import com.naniger.arzikina.di.IoDispatcher
import com.naniger.arzikina.domain.model.SyncOperation
import com.naniger.arzikina.domain.model.TransactionTemplate
import com.naniger.arzikina.domain.repository.SessionManager
import com.naniger.arzikina.domain.repository.TransactionTemplateRepository
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
 * Implémentation Room de [TransactionTemplateRepository] — voir sa doc de tête.
 *
 * Isolation multi-utilisateurs : voir `AccountRepositoryImpl` pour le raisonnement.
 *
 * Synchronisée depuis l'extension "Marketplace personnelle" du chantier de synchronisation (voir
 * `server/api/config/entity_sync_configs.php`, entrée `transaction_templates`, et
 * `database/migrations/005_add_transaction_templates.sql`) — auparavant volontairement locale
 * (suppression matérielle, aucun enfilage `sync_queue`), même situation que `ReceiptRepositoryImpl`
 * avant elle. Dépend directement de [AccountDao]/[CategoryDao] (pas de `AccountRepository`/
 * `CategoryRepository`) pour résoudre `accountSyncId`/`categorySyncId` — même raisonnement que
 * `RecurringTransactionRepositoryImpl`, dont cette classe reprend le principe d'enfilage : pas de
 * classe partagée type `LoanSyncEnqueuer`/`CategorySyncEnqueuer` (un seul propriétaire d'écriture
 * pour cette entité, voir la KDoc de tête de `TransactionTemplateSyncPayload.kt`).
 */
class TransactionTemplateRepositoryImpl @Inject constructor(
    private val templateDao: TransactionTemplateDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val sessionManager: SessionManager,
    private val syncQueueEnqueuer: SyncQueueEnqueuer,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : TransactionTemplateRepository {

    override fun observeTemplates(): Flow<List<TransactionTemplate>> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                templateDao.observeAllForUser(userId).map { entities -> entities.map { it.toDomain() } }
            }
        }

    override suspend fun getTemplate(id: Long): TransactionTemplate? =
        withContext(ioDispatcher) { templateDao.getById(id, requireCurrentUserId())?.toDomain() }

    /**
     * Enfile une entrée `sync_queue` (CREATE/UPDATE) après l'écriture Room habituelle — voir
     * [enqueueTemplateSync]. `syncId`/`version` sont PRÉSERVÉS d'une modification à l'autre (jamais
     * réinitialisés par `toEntity`, qui ignore volontairement ces champs) — même raisonnement que
     * `CategoryRepositoryImpl.saveCategory`.
     */
    override suspend fun saveTemplate(template: TransactionTemplate): Long = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val now = System.currentTimeMillis()
        var pendingOp: Pair<TransactionTemplateEntity, SyncOperation>? = null

        val id = if (template.id == 0L) {
            val entity = template.copy(createdAt = now, updatedAt = now).toEntity(userId)
                .copy(syncId = UUID.randomUUID().toString())
            val generatedId = templateDao.upsert(entity)
            pendingOp = entity.copy(id = generatedId) to SyncOperation.CREATE
            generatedId
        } else {
            val existing = templateDao.getById(template.id, userId) ?: error("Modèle introuvable.")
            val entity = template.copy(createdAt = existing.createdAt, updatedAt = now).toEntity(userId).copy(
                syncId = existing.syncId ?: UUID.randomUUID().toString(),
                deletedAt = existing.deletedAt,
                version = existing.version
            )
            templateDao.upsert(entity)
            pendingOp = entity to SyncOperation.UPDATE
            template.id
        }

        pendingOp?.let { (entity, operation) -> enqueueTemplateSync(entity, operation) }
        id
    }

    override suspend fun duplicateTemplate(id: Long): Long = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val existing = templateDao.getById(id, userId) ?: error("Modèle introuvable.")
        val now = System.currentTimeMillis()
        // Une copie est une nouvelle ligne à part entière : `syncId`/`version` neufs, jamais ceux
        // du modèle dupliqué (voir la doc de tête de `MarketplaceViewModel.onDuplicate`).
        val duplicate = existing.copy(
            id = 0L,
            name = "${existing.name} (copie)",
            isFavorite = false,
            createdAt = now,
            updatedAt = now,
            syncId = UUID.randomUUID().toString(),
            deletedAt = null,
            version = 1
        )
        val generatedId = templateDao.upsert(duplicate)
        enqueueTemplateSync(duplicate.copy(id = generatedId), SyncOperation.CREATE)
        generatedId
    }

    override suspend fun setFavorite(id: Long, isFavorite: Boolean): Unit = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val existing = templateDao.getById(id, userId) ?: error("Modèle introuvable.")
        val entity = existing.copy(
            isFavorite = isFavorite,
            updatedAt = System.currentTimeMillis(),
            syncId = existing.syncId ?: UUID.randomUUID().toString()
        )
        templateDao.upsert(entity)
        enqueueTemplateSync(entity, SyncOperation.UPDATE)
    }

    /**
     * Suppression DOUCE (voir `TransactionTemplateDao.softDeleteById`) : la ligne reste
     * physiquement en base — nécessaire pour connaître son `syncId` et enfiler l'opération DELETE.
     * Silencieux (pas d'erreur) si [id] n'existe pas/plus/appartient à un autre utilisateur, même
     * comportement que l'ancienne suppression physique — voir `CategoryRepositoryImpl.deleteCategory`
     * pour le même raisonnement complet.
     */
    override suspend fun deleteTemplate(id: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val existing = templateDao.getById(id, userId) ?: return@withContext
        val now = System.currentTimeMillis()

        templateDao.softDeleteById(id, userId, now)
        enqueueTemplateSync(
            existing.copy(syncId = existing.syncId ?: UUID.randomUUID().toString(), deletedAt = now, updatedAt = now),
            SyncOperation.DELETE
        )
    }

    private suspend fun enqueueTemplateSync(entity: TransactionTemplateEntity, operation: SyncOperation) {
        val payload = TransactionTemplateSyncPayload(
            id = requireNotNull(entity.syncId) { "syncId doit être généré avant l'enfilage." },
            baseVersion = if (operation == SyncOperation.CREATE) null else entity.version,
            name = entity.name,
            type = entity.type.name,
            amount = entity.amount,
            categorySyncId = resolveCategorySyncId(entity.categoryId, entity.userId),
            accountSyncId = resolveAccountSyncId(entity.accountId, entity.userId),
            description = entity.description,
            isFavorite = entity.isFavorite,
            defaultHour = entity.defaultHour,
            defaultMinute = entity.defaultMinute,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
        syncQueueEnqueuer.enqueue(
            entityType = "transaction_templates",
            entitySyncId = payload.id,
            operation = operation,
            payloadJson = json.encodeToString(TransactionTemplateSyncPayload.serializer(), payload)
        )
    }

    /** La ligne elle-même DOIT exister (contrainte `ForeignKey.CASCADE` réelle sur `accountId` —
     *  voir `TransactionTemplateEntity`) : son absence serait une corruption de données, pas un cas
     *  à absorber silencieusement, d'où `error()`. `getByIdIncludingDeleted` (pas `getById`) : voir
     *  `RecurringTransactionRepositoryImpl.resolveAccountSyncId` pour le raisonnement complet
     *  (filet de sécurité contre une référence déjà soft-supprimée dans la même cascade). */
    private suspend fun resolveAccountSyncId(accountId: Long, userId: Long): String {
        val account = accountDao.getByIdIncludingDeleted(accountId, userId)
            ?: error("Compte introuvable pour ce modèle (accountId=$accountId).")
        return resolveOrAssignSyncId(account.syncId) { newSyncId -> accountDao.upsert(account.copy(syncId = newSyncId)) }
    }

    /** [categoryId] TOUJOURS renseigné pour un modèle (voir la doc de tête de
     * [com.naniger.arzikina.domain.model.TransactionTemplate]) : contrairement à
     * `RecurringTransactionRepositoryImpl.resolveCategorySyncId`, aucun appelant ne passe jamais de
     * valeur absente ici — signature non-nullable directement. */
    private suspend fun resolveCategorySyncId(categoryId: Long, userId: Long): String {
        val category = categoryDao.getByIdIncludingDeleted(categoryId, userId)
            ?: error("Catégorie introuvable pour ce modèle (categoryId=$categoryId).")
        return resolveOrAssignSyncId(category.syncId) { newSyncId -> categoryDao.upsert(category.copy(syncId = newSyncId)) }
    }

    /** Voir `RecurringTransactionRepositoryImpl.resolveOrAssignSyncId` (même filet de sécurité,
     *  dupliqué ici volontairement : un seul appelant, une classe partagée n'apporterait rien). */
    private suspend fun resolveOrAssignSyncId(existing: String?, persist: suspend (String) -> Unit): String {
        existing?.let { return it }
        val newSyncId = UUID.randomUUID().toString()
        persist(newSyncId)
        return newSyncId
    }

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
