package com.arzikina.ne.data.repository

import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.local.dao.SavingsGoalDao
import com.arzikina.ne.data.local.dao.SyncQueueDao
import com.arzikina.ne.data.local.entity.CategoryEntity
import com.arzikina.ne.data.local.entity.SavingsGoalEntity
import com.arzikina.ne.data.local.entity.SyncQueueEntity
import com.arzikina.ne.data.remote.api.SyncApi
import com.arzikina.ne.data.remote.dto.CategoryServerStateDto
import com.arzikina.ne.data.remote.dto.SavingsGoalServerStateDto
import com.arzikina.ne.data.remote.dto.SyncPushOperationDto
import com.arzikina.ne.data.remote.dto.SyncPushRequestDto
import com.arzikina.ne.domain.model.CategoryIcon
import com.arzikina.ne.domain.model.SyncEngineResult
import com.arzikina.ne.domain.model.SyncOperation
import com.arzikina.ne.domain.model.SyncPullResult
import com.arzikina.ne.domain.model.SyncStatus
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.SyncEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Voir [SyncEngine] pour le contrat et l'étape actuelle. [SUPPORTED_ENTITY_TYPES] (`categories`,
 * `savings_goals`) sont traitées en DUR ici — même choix que `server/api/sync/push.php` côté
 * serveur (voir sa doc de tête) : pas d'abstraction générique par type d'entité tant qu'une
 * TROISIÈME entité ne vient pas confirmer le motif à en extraire (règle de trois). Seule la boucle
 * EXTERNE (drainage de la file, pagination, curseur, transitions de statut) est déjà partagée entre
 * les deux — voir [pushBatch]/[pullEntityType] — seules [applyCategoryServerState]/
 * [applySavingsGoalServerState] (et la construction du payload, faite en amont par chaque
 * repository) diffèrent réellement par entité.
 *
 * Dépend directement de [CategoryDao]/[SavingsGoalDao] (couche DATA vers couche DATA, jamais via
 * leurs repositories respectifs, qui filtrent par utilisateur COURANT et masquent volontairement
 * `syncId`/`version` au domaine — voir leur KDoc) : ce moteur doit pouvoir relire/écrire ces champs
 * bruts, y compris sur des lignes déjà supprimées (voir `getBySyncId` de chaque DAO).
 */
@Singleton
class SyncEngineImpl @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val categoryDao: CategoryDao,
    private val savingsGoalDao: SavingsGoalDao,
    private val syncApi: SyncApi,
    private val syncCursorStore: SyncCursorStore,
    private val sessionManager: SessionManager,
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
     * Un type d'entité NON encore câblé est silencieusement ignoré — ses entrées restent
     * `PENDING`, rien n'est perdu, elles seront traitées le jour où ce type sera câblé ici, sans
     * migration de données nécessaire. La construction de la requête est déjà générique
     * (`payloadJson` pré-sérialisé par le repository d'origine, voir `SyncQueueEnqueuer`) — seule
     * l'application du résultat ([applyServerEntity]) dépend du type d'entité.
     */
    private suspend fun pushBatch(entityType: String, entries: List<SyncQueueEntity>): Pair<Int, Int> {
        if (entityType !in SUPPORTED_ENTITY_TYPES) return 0 to 0

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
                        // CategoryRepositoryImpl.deleteCategory/SavingsGoalRepositoryImpl.deleteSavingsGoal)
                        // — l'état voulu (absente du serveur) est déjà atteint, ce n'est pas un échec.
                        markSynced(entry)
                        succeeded++
                    } else {
                        markFailed(listOf(entry), errorMessage = result.errorCode)
                        failed++
                    }
                }
                else -> {
                    try {
                        // allowCreate = false : un résultat de PUSH ne concerne jamais que des
                        // lignes que CET appareil possède déjà localement (c'est lui qui les a
                        // envoyées) — contrairement à pullEntityType ci-dessous, qui peut recevoir
                        // des lignes jamais vues sur cet appareil (voir sa KDoc).
                        result.serverEntity?.let { applyServerEntity(entityType, it, allowCreate = false) }
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

    /** Voir [SyncEngine.pullRemoteChanges] — chaque type d'entité a son PROPRE curseur
     *  ([SyncCursorStore]), donc son propre appel à [pullEntityType]. */
    override suspend fun pullRemoteChanges(): SyncPullResult {
        var received = 0
        var applied = 0
        SUPPORTED_ENTITY_TYPES.forEach { entityType ->
            val (entityReceived, entityApplied) = pullEntityType(entityType)
            received += entityReceived
            applied += entityApplied
        }
        return SyncPullResult(received = received, applied = applied)
    }

    /**
     * Boucle jusqu'à recevoir un lot VIDE pour [entityType] (plutôt que de s'appuyer sur la
     * constante "500" du plafond serveur, voir la doc de tête de `pull.php`) : un lot plus petit
     * que le plafond signifie "tout reçu", et un lot vide déclenché par une relance après un lot
     * plein exact ne coûte qu'un aller-retour réseau supplémentaire, sans risque de boucle infinie
     * ni de lecture incomplète.
     *
     * Le curseur ([SyncCursorStore]) avance APRÈS CHAQUE lot appliqué (pas seulement à la fin) :
     * une interruption (perte réseau, app tuée) entre deux lots ne fait jamais retraiter ceux déjà
     * appliqués au prochain pull.
     */
    private suspend fun pullEntityType(entityType: String): Pair<Int, Int> {
        var received = 0
        var applied = 0
        var cursor = syncCursorStore.getLastPulledAt(entityType)

        while (true) {
            val response = try {
                syncApi.pull(entityType, cursor)
            } catch (e: IOException) {
                break // Échec réseau/serveur : le curseur n'a pas avancé, rien n'est perdu.
            }

            response.entities.forEach { element ->
                received++
                runCatching { applyServerEntity(entityType, element, allowCreate = true) }.onSuccess { applied++ }
                // Ligne malformée ignorée silencieusement (voir SyncPullResult.received/applied) :
                // ne bloque jamais le reste du lot, ni les autres types d'entité.
            }

            cursor = response.serverTime
            syncCursorStore.setLastPulledAt(entityType, cursor)

            if (response.entities.isEmpty()) break
        }

        return received to applied
    }

    /** Décode [element] selon [entityType] puis délègue à la fonction d'application dédiée — SEUL
     *  point de dispatch par type d'entité de cette classe (voir la doc de tête). */
    private suspend fun applyServerEntity(entityType: String, element: JsonElement, allowCreate: Boolean) {
        when (entityType) {
            "categories" ->
                applyCategoryServerState(json.decodeFromJsonElement(CategoryServerStateDto.serializer(), element), allowCreate)
            "savings_goals" ->
                applySavingsGoalServerState(json.decodeFromJsonElement(SavingsGoalServerStateDto.serializer(), element), allowCreate)
        }
    }

    /**
     * Applique l'état confirmé par le serveur sur la ligne locale correspondant à `state.id`
     * (`syncId`) — partagé par [pushBatch] (résultat d'un envoi) et [pullEntityType] (changement
     * distant). [allowCreate] : `false` depuis un résultat de push ; `true` depuis un pull, où la
     * ligne peut être totalement INCONNUE de cet appareil (créée sur un autre appareil, ou
     * existante avant l'installation courante) — dans ce cas
     * [SessionManager.getCurrentUserIdOnce] fournit le `userId` LOCAL (jamais celui du serveur,
     * voir la KDoc de [CategoryServerStateDto]) de la nouvelle ligne Room.
     */
    private suspend fun applyCategoryServerState(state: CategoryServerStateDto, allowCreate: Boolean) {
        val local = categoryDao.getBySyncId(state.id)
        if (local == null && !allowCreate) return
        val userId = local?.userId ?: sessionManager.getCurrentUserIdOnce() ?: return

        categoryDao.upsert(
            CategoryEntity(
                id = local?.id ?: 0L,
                userId = userId,
                name = state.name,
                icon = runCatching { CategoryIcon.valueOf(state.icon) }.getOrDefault(local?.icon ?: CategoryIcon.OTHER),
                colorArgb = state.colorArgb,
                type = runCatching { TransactionType.valueOf(state.type) }.getOrDefault(local?.type ?: TransactionType.EXPENSE),
                createdAt = state.createdAt,
                syncId = state.id,
                updatedAt = state.updatedAt,
                deletedAt = state.deletedAt,
                version = state.version
            )
        )
    }

    /** Voir [applyCategoryServerState] — même logique, appliquée à `savings_goals`. */
    private suspend fun applySavingsGoalServerState(state: SavingsGoalServerStateDto, allowCreate: Boolean) {
        val local = savingsGoalDao.getBySyncId(state.id)
        if (local == null && !allowCreate) return
        val userId = local?.userId ?: sessionManager.getCurrentUserIdOnce() ?: return

        savingsGoalDao.upsert(
            SavingsGoalEntity(
                id = local?.id ?: 0L,
                userId = userId,
                name = state.name,
                targetAmount = state.targetAmount,
                currentAmount = state.currentAmount,
                currencyCode = state.currencyCode,
                deadline = state.deadline,
                createdAt = state.createdAt,
                syncId = state.id,
                updatedAt = state.updatedAt,
                deletedAt = state.deletedAt,
                version = state.version
            )
        )
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

    private companion object {
        val SUPPORTED_ENTITY_TYPES = setOf("categories", "savings_goals")
        const val MAX_ERROR_MESSAGE_LENGTH = 200
    }
}
