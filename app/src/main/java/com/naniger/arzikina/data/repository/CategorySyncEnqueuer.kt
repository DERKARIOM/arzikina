package com.naniger.arzikina.data.repository

import com.naniger.arzikina.data.local.entity.CategoryEntity
import com.naniger.arzikina.data.remote.dto.CategorySyncPayload
import com.naniger.arzikina.domain.model.SyncOperation
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Point d'entrée UNIQUE pour enfiler la synchronisation d'une [CategoryEntity] — même raisonnement
 * que [TransactionSyncEnqueuer]/[LoanSyncEnqueuer] (voir leur KDoc de tête). Extrait de
 * `CategoryRepositoryImpl.enqueueCategorySync` (jusque-là privée, seul appelant) au moment où
 * `com.arzikina.ne.data.local.database.SystemCategoryResolver` (catégories système Prêts/Frais,
 * recréées silencieusement si supprimées entre-temps) en a eu besoin lui aussi : dupliquer cette
 * construction de payload aurait violé "évite absolument le code dupliqué" (cahier des charges), et
 * surtout aurait laissé un second endroit désynchronisé du premier en cas d'évolution future.
 *
 * Bug réel corrigé par cette extraction : `SystemCategoryResolver` recréait une catégorie système
 * localement (`categoryDao.upsert`) sans jamais l'enfiler — elle restait alors orpheline pour
 * toujours dès qu'une transaction en tirait un `syncId` de secours (voir le filet de sécurité de
 * [TransactionSyncEnqueuer.resolveOrAssignSyncId]) : ce `syncId`, une fois assigné, sortait la
 * catégorie du périmètre de [SyncEngineImpl.enqueueUnsyncedLocalData] (qui ne reprend que les lignes
 * `syncId IS NULL`), sans jamais avoir été réellement poussée au serveur.
 */
@Singleton
class CategorySyncEnqueuer @Inject constructor(
    private val syncQueueEnqueuer: SyncQueueEnqueuer,
    private val json: Json
) {
    suspend fun enqueue(entity: CategoryEntity, operation: SyncOperation) {
        val payload = CategorySyncPayload(
            id = requireNotNull(entity.syncId) { "syncId doit être généré avant l'enfilage." },
            baseVersion = if (operation == SyncOperation.CREATE) null else entity.version,
            name = entity.name,
            icon = entity.icon.name,
            colorArgb = entity.colorArgb,
            type = entity.type.name,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
        syncQueueEnqueuer.enqueue(
            entityType = "categories",
            entitySyncId = payload.id,
            operation = operation,
            payloadJson = json.encodeToString(CategorySyncPayload.serializer(), payload)
        )
    }
}
