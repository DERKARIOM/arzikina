package com.arzikina.ne.data.repository

import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.local.entity.CategoryEntity
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.SyncOperation
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.CategoryRepository
import com.arzikina.ne.domain.repository.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/** Isolation multi-utilisateurs : voir `AccountRepositoryImpl` pour le raisonnement. */
class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
    private val sessionManager: SessionManager,
    private val categorySyncEnqueuer: CategorySyncEnqueuer,
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

    /**
     * Enfile une entrée `sync_queue` (CREATE/UPDATE) après l'écriture Room habituelle — voir
     * [SyncQueueEnqueuer]. [CategoryEntity.syncId]/[CategoryEntity.version] sont PRÉSERVÉS d'une
     * modification à l'autre (jamais réinitialisés par [toEntity], qui ignore volontairement ces
     * champs — voir sa KDoc) : une catégorie déjà envoyée au serveur garde le même identifiant
     * partagé, et [CategoryEntity.version] ne progresse QUE lorsque le futur Sync Engine applique
     * un `serverEntity` confirmé (jamais lors d'un simple enregistrement local, voir la KDoc de
     * [CategorySyncPayload.baseVersion]).
     *
     * `deleteCategory` ci-dessous est désormais câblé sur la même file (suppression douce, voir sa
     * propre KDoc) — les deux méthodes de cette classe partagent [categorySyncEnqueuer].
     */
    override suspend fun saveCategory(category: Category) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val existing = if (category.id != 0L) categoryDao.getById(category.id, userId) else null
        val now = System.currentTimeMillis()

        val entity = category.toEntity(userId).copy(
            syncId = existing?.syncId ?: UUID.randomUUID().toString(),
            createdAt = existing?.createdAt ?: category.createdAt,
            updatedAt = now,
            deletedAt = existing?.deletedAt,
            version = existing?.version ?: 1
        )
        categoryDao.upsert(entity)
        categorySyncEnqueuer.enqueue(entity, operation = if (existing == null) SyncOperation.CREATE else SyncOperation.UPDATE)
    }

    /**
     * Suppression DOUCE (voir `CategoryDao.softDeleteById`, `CategoryEntity.deletedAt`) : la ligne
     * reste physiquement en base — nécessaire pour connaître son [CategoryEntity.syncId] et enfiler
     * l'opération DELETE. Silencieux (pas d'erreur) si [id] n'existe pas/plus/appartient à un autre
     * utilisateur, même comportement que l'ancienne suppression physique (`DELETE ... WHERE id AND
     * userId` ne levait déjà aucune erreur sur 0 ligne affectée).
     *
     * `syncId ?: UUID.randomUUID()...` : même filet de sécurité que [saveCategory] — une catégorie
     * jamais modifiée depuis l'ajout de la synchronisation (ex. catégorie par défaut créée par
     * `NewUserDefaultDataSeeder`, en dehors de ce repository) n'a encore aucun `syncId`. Généré ici
     * UNIQUEMENT pour le payload envoyé (pas besoin de le persister : la ligne est supprimée, ce
     * `syncId` ne sera plus jamais relu localement) — si le serveur ne connaît pas non plus cette
     * ligne, `push.php` répond `not_found`, sans erreur bloquante (voir sa doc).
     */
    override suspend fun deleteCategory(id: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val existing = categoryDao.getById(id, userId) ?: return@withContext
        val now = System.currentTimeMillis()

        categoryDao.softDeleteById(id, userId, now)
        categorySyncEnqueuer.enqueue(
            existing.copy(syncId = existing.syncId ?: UUID.randomUUID().toString(), deletedAt = now, updatedAt = now),
            operation = SyncOperation.DELETE
        )
    }

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
