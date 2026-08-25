package com.arzikina.ne.data.repository

import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.local.dao.SyncQueueDao
import com.arzikina.ne.data.local.entity.SyncQueueEntity
import com.arzikina.ne.data.remote.api.SyncApi
import com.arzikina.ne.data.remote.dto.CategoryServerStateDto
import com.arzikina.ne.data.remote.dto.SyncPushOperationDto
import com.arzikina.ne.data.remote.dto.SyncPushRequestDto
import com.arzikina.ne.domain.model.CategoryIcon
import com.arzikina.ne.domain.model.SyncEngineResult
import com.arzikina.ne.domain.model.SyncOperation
import com.arzikina.ne.domain.model.SyncStatus
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.SyncEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Voir [SyncEngine] pour le contrat et l'étape actuelle. Seul [SUPPORTED_ENTITY_TYPE] (`categories`)
 * est traité en dur ici — même choix que `server/api/sync/push.php` côté serveur (voir sa doc de
 * tête) : pas d'abstraction générique par type d'entité tant qu'une SEULE entité n'est câblée, pour
 * ne pas imposer une forme qu'un deuxième cas d'usage réel pourrait remettre en cause.
 *
 * Dépend directement de [CategoryDao] (couche DATA vers couche DATA, jamais via
 * [com.arzikina.ne.domain.repository.CategoryRepository], qui filtre par utilisateur COURANT et
 * masque volontairement `syncId`/`version` au domaine — voir sa KDoc) : ce moteur doit pouvoir
 * relire/écrire ces champs bruts, y compris sur des lignes déjà supprimées (voir
 * [CategoryDao.getBySyncId]).
 */
@Singleton
class SyncEngineImpl @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val categoryDao: CategoryDao,
    private val syncApi: SyncApi,
    private val json: Json
) : SyncEngine {

    override suspend fun pushPendingChanges(): SyncEngineResult {
        val pendingEntries = syncQueueDao.getByStatus(SyncStatus.PENDING)
        if (pendingEntries.isEmpty()) return SyncEngineResult(pushed = 0, succeeded = 0, failed = 0)

        var succeeded = 0
        var failed = 0
        pendingEntries.groupBy { it.entityType }.forEach { (entityType, entries) ->
            val (batchSucceeded, batchFailed) = pushBatch(entityType, entries)
            succeeded += batchSucceeded
            failed += batchFailed
        }
        return SyncEngineResult(pushed = pendingEntries.size, succeeded = succeeded, failed = failed)
    }

    /**
     * Un type d'entité NON encore câblé (rien d'autre que `categories` aujourd'hui) est
     * silencieusement ignoré — ses entrées restent `PENDING`, rien n'est perdu, elles seront
     * traitées le jour où ce type sera câblé ici, sans migration de données nécessaire.
     */
    private suspend fun pushBatch(entityType: String, entries: List<SyncQueueEntity>): Pair<Int, Int> {
        if (entityType != SUPPORTED_ENTITY_TYPE) return 0 to 0

        markSyncing(entries)

        val request = SyncPushRequestDto(
            entityType = entityType,
            operations = entries.map { entry ->
                SyncPushOperationDto(operation = entry.operation.name, entity = json.parseToJsonElement(entry.payloadJson))
            }
        )

        val response = try {
            syncApi.push(request)
        } catch (e: IOException) {
            // Échec réseau/serveur global (voir la KDoc de SyncStatus) : toutes les entrées du lot
            // repassent FAILED, aucune n'est perdue — une future planification (WorkManager) les
            // reprendra.
            markFailed(entries, errorMessage = e.message)
            return 0 to entries.size
        }

        var succeeded = 0
        var failed = 0
        entries.forEachIndexed { index, entry ->
            val result = response.results.getOrNull(index)
            when {
                result == null -> {
                    markFailed(listOf(entry), errorMessage = "Réponse serveur incomplète")
                    failed++
                }
                result.status == "error" -> {
                    if (entry.operation == SyncOperation.DELETE && result.errorCode == "not_found") {
                        // Idempotent : le serveur ne connaissait déjà pas cette ligne (jamais
                        // envoyée avec succès avant sa suppression, voir la KDoc de
                        // CategoryRepositoryImpl.deleteCategory) — l'état voulu (absente du
                        // serveur) est déjà atteint, ce n'est pas un échec.
                        markSynced(entry)
                        succeeded++
                    } else {
                        markFailed(listOf(entry), errorMessage = result.errorCode)
                        failed++
                    }
                }
                else -> {
                    try {
                        applyServerState(result.serverEntity)
                        markSynced(entry)
                        succeeded++
                    } catch (e: Exception) {
                        markFailed(listOf(entry), errorMessage = e.message)
                        failed++
                    }
                }
            }
        }
        return succeeded to failed
    }

    private suspend fun markSyncing(entries: List<SyncQueueEntity>) {
        entries.forEach { syncQueueDao.update(it.copy(status = SyncStatus.SYNCING)) }
    }

    private suspend fun markSynced(entry: SyncQueueEntity) {
        syncQueueDao.update(
            entry.copy(status = SyncStatus.SYNCED, lastAttemptAt = System.currentTimeMillis(), errorMessage = null)
        )
    }

    private suspend fun markFailed(entries: List<SyncQueueEntity>, errorMessage: String?) {
        val now = System.currentTimeMillis()
        entries.forEach { entry ->
            syncQueueDao.update(
                entry.copy(
                    status = SyncStatus.FAILED,
                    retryCount = entry.retryCount + 1,
                    lastAttemptAt = now,
                    // Message COURT (voir SyncQueueEntity.errorMessage), jamais affiché brut à
                    // l'utilisateur — diagnostic uniquement.
                    errorMessage = errorMessage?.take(MAX_ERROR_MESSAGE_LENGTH)
                )
            )
        }
    }

    /** Applique l'état confirmé par le serveur sur la ligne locale (voir la KDoc de
     *  [com.arzikina.ne.data.remote.dto.SyncPushResultDto]) — `null`/lignes introuvables ignorées
     *  silencieusement (rien à appliquer, ou ligne déjà absente localement). */
    private suspend fun applyServerState(serverEntity: JsonElement?) {
        val state = serverEntity?.let { json.decodeFromJsonElement(CategoryServerStateDto.serializer(), it) } ?: return
        val local = categoryDao.getBySyncId(state.id) ?: return
        categoryDao.upsert(
            local.copy(
                name = state.name,
                icon = runCatching { CategoryIcon.valueOf(state.icon) }.getOrDefault(local.icon),
                colorArgb = state.colorArgb,
                type = runCatching { TransactionType.valueOf(state.type) }.getOrDefault(local.type),
                createdAt = state.createdAt,
                updatedAt = state.updatedAt,
                deletedAt = state.deletedAt,
                version = state.version
            )
        )
    }

    private companion object {
        const val SUPPORTED_ENTITY_TYPE = "categories"
        const val MAX_ERROR_MESSAGE_LENGTH = 200
    }
}
