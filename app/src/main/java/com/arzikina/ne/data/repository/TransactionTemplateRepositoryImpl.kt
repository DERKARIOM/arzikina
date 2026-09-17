package com.arzikina.ne.data.repository

import com.arzikina.ne.data.local.dao.TransactionTemplateDao
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.TransactionTemplate
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.TransactionTemplateRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implémentation Room de [TransactionTemplateRepository] — voir sa doc de tête.
 *
 * Isolation multi-utilisateurs : voir `AccountRepositoryImpl` pour le raisonnement.
 *
 * Volontairement SANS synchronisation serveur pour l'instant (pas de `SyncQueueEnqueuer`, pas
 * d'enfilage `sync_queue`) — mêmes champs `syncId`/`deletedAt`/`version` posés sur l'entité que les
 * autres (voir sa doc), mais pas encore activement poussés, exactement comme `ReceiptRepositoryImpl`
 * à sa création. Suppression matérielle (`deleteById`, pas de suppression douce) pour la même
 * raison — cette étape sera revue explicitement quand la synchronisation de cette fonctionnalité
 * sera activée (voir cahier des charges "Marketplace personnelle", section 10).
 */
class TransactionTemplateRepositoryImpl @Inject constructor(
    private val templateDao: TransactionTemplateDao,
    private val sessionManager: SessionManager,
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

    override suspend fun saveTemplate(template: TransactionTemplate): Long = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val now = System.currentTimeMillis()
        if (template.id == 0L) {
            templateDao.upsert(template.copy(createdAt = now, updatedAt = now).toEntity(userId))
        } else {
            val existing = templateDao.getById(template.id, userId) ?: error("Modèle introuvable.")
            templateDao.upsert(
                template.copy(createdAt = existing.createdAt, updatedAt = now).toEntity(userId)
            )
            template.id
        }
    }

    override suspend fun duplicateTemplate(id: Long): Long = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val existing = templateDao.getById(id, userId) ?: error("Modèle introuvable.")
        val now = System.currentTimeMillis()
        templateDao.upsert(
            existing.copy(
                id = 0L,
                name = "${existing.name} (copie)",
                isFavorite = false,
                createdAt = now,
                updatedAt = now,
                syncId = null
            )
        )
    }

    override suspend fun setFavorite(id: Long, isFavorite: Boolean): Unit = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val existing = templateDao.getById(id, userId) ?: error("Modèle introuvable.")
        templateDao.upsert(existing.copy(isFavorite = isFavorite, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteTemplate(id: Long) = withContext(ioDispatcher) {
        templateDao.deleteById(id, requireCurrentUserId())
    }

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
