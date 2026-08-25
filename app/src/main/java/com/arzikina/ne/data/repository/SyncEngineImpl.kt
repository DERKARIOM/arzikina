package com.arzikina.ne.data.repository

import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.local.dao.FinancialPlanDao
import com.arzikina.ne.data.local.dao.SavingsGoalDao
import com.arzikina.ne.data.local.dao.SyncQueueDao
import com.arzikina.ne.data.local.entity.CategoryEntity
import com.arzikina.ne.data.local.entity.FinancialPlanEntity
import com.arzikina.ne.data.local.entity.SavingsGoalEntity
import com.arzikina.ne.data.local.entity.SyncQueueEntity
import com.arzikina.ne.data.remote.api.SyncApi
import com.arzikina.ne.data.remote.dto.CategoryServerStateDto
import com.arzikina.ne.data.remote.dto.CategorySyncPayload
import com.arzikina.ne.data.remote.dto.FinancialPlanServerStateDto
import com.arzikina.ne.data.remote.dto.FinancialPlanSyncPayload
import com.arzikina.ne.data.remote.dto.SavingsGoalServerStateDto
import com.arzikina.ne.data.remote.dto.SavingsGoalSyncPayload
import com.arzikina.ne.data.remote.dto.SyncPushOperationDto
import com.arzikina.ne.data.remote.dto.SyncPushRequestDto
import com.arzikina.ne.domain.model.CategoryIcon
import com.arzikina.ne.domain.model.FinancialPlanIcon
import com.arzikina.ne.domain.model.PlanPeriodType
import com.arzikina.ne.domain.model.PlanStatus
import com.arzikina.ne.domain.model.SyncEngineResult
import com.arzikina.ne.domain.model.SyncOperation
import com.arzikina.ne.domain.model.SyncPullResult
import com.arzikina.ne.domain.model.SyncQueueStatus
import com.arzikina.ne.domain.model.SyncStatus
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.SyncEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Voir [SyncEngine] pour le contrat et l'étape actuelle. [SUPPORTED_ENTITY_TYPES] (`categories`,
 * `savings_goals`, `financial_plans`) sont traitées en DUR ici — même choix que
 * `server/api/sync/push.php` côté serveur (voir sa doc de tête) : la règle de trois est atteinte
 * avec `financial_plans`, mais la généralisation reste volontairement une étape SÉPARÉE et
 * différée (voir le plan validé), pour ne jamais mélanger une nouvelle entité et un refactor
 * comportemental dans le même changement à vérifier. Seule la boucle EXTERNE (drainage de la file,
 * pagination, curseur, transitions de statut) est déjà partagée entre les trois — voir
 * [pushBatch]/[pullEntityType] — seules [applyCategoryServerState]/[applySavingsGoalServerState]/
 * [applyFinancialPlanServerState] (et la construction du payload, faite en amont par chaque
 * repository) diffèrent réellement par entité.
 *
 * Dépend directement de [CategoryDao]/[SavingsGoalDao]/[FinancialPlanDao] (couche DATA vers couche
 * DATA, jamais via leurs repositories respectifs, qui filtrent par utilisateur COURANT et masquent
 * volontairement `syncId`/`version` au domaine — voir leur KDoc) : ce moteur doit pouvoir
 * relire/écrire ces champs bruts, y compris sur des lignes déjà supprimées (voir `getBySyncId` de
 * chaque DAO).
 */
@Singleton
class SyncEngineImpl @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val categoryDao: CategoryDao,
    private val savingsGoalDao: SavingsGoalDao,
    private val financialPlanDao: FinancialPlanDao,
    private val syncApi: SyncApi,
    private val syncCursorStore: SyncCursorStore,
    private val syncQueueEnqueuer: SyncQueueEnqueuer,
    private val sessionManager: SessionManager,
    private val json: Json
) : SyncEngine {

    /**
     * `PENDING` ET `FAILED` (pas seulement `PENDING`) : sans ce second statut, une entrée passée en
     * échec une fois (ex. serveur temporairement indisponible, bug côté serveur depuis corrigé)
     * n'était plus JAMAIS retentée — bug réel rencontré en pratique lors du câblage de
     * `financial_plans` (déploiement serveur momentanément désynchronisé de l'app). `SYNCING`/
     * `SYNCED` restent exclus (déjà en cours ou déjà confirmées).
     *
     * RISQUE ASSUMÉ ET SIGNALÉ : une entrée en échec pour une raison PERMANENTE (donnée invalide
     * qui ne passera jamais côté serveur, pas un simple souci réseau/déploiement transitoire) sera
     * retentée INDÉFINIMENT à chaque appel — manuel (`SettingsViewModel.syncNow`) ou automatique
     * (toutes les [com.arzikina.ne.work.SyncWorkScheduler.INTERVAL_HOURS] heures). `retryCount`
     * (voir `SyncQueueEntity`) est déjà suivi mais volontairement PAS encore utilisé pour plafonner
     * ces tentatives — à revisiter dans une étape dédiée si ce cas se présente réellement en
     * pratique (voir `retryCount` incrémenté à chaque échec par [markFailed], prêt à servir de base
     * à une limite future).
     */
    override suspend fun pushPendingChanges(): SyncEngineResult {
        val pendingEntries = syncQueueDao.getByStatus(SyncStatus.PENDING) + syncQueueDao.getByStatus(SyncStatus.FAILED)
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
            "financial_plans" ->
                applyFinancialPlanServerState(json.decodeFromJsonElement(FinancialPlanServerStateDto.serializer(), element), allowCreate)
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

    /**
     * Voir [SyncEngine.observeQueueStatus]. Volontairement TOUS types d'entités confondus (pas de
     * paramètre [entityType]) : l'indicateur visuel visé (écran Paramètres) affiche un état global
     * unique de la synchronisation, pas un état par entité — inutile d'exposer cette granularité à
     * la Présentation pour l'instant. `combine` (pas trois collectes séparées côté appelant) :
     * une seule émission recomposée à chaque changement de N'IMPORTE LEQUEL des trois décomptes,
     * jamais un état partiellement à jour.
     */
    override fun observeQueueStatus(): Flow<SyncQueueStatus> = combine(
        syncQueueDao.observeCountByStatus(SyncStatus.PENDING),
        syncQueueDao.observeCountByStatus(SyncStatus.SYNCING),
        syncQueueDao.observeCountByStatus(SyncStatus.FAILED)
    ) { pending, syncing, failed -> SyncQueueStatus(pending = pending, syncing = syncing, failed = failed) }

    /** Voir [SyncEngine.enqueueUnsyncedLocalData]. Ne fait rien silencieusement sans utilisateur
     *  courant (même garde que [applyCategoryServerState]/[applySavingsGoalServerState]) : cet
     *  appel suit toujours un `login` réussi, un utilisateur devrait donc déjà être connu. */
    override suspend fun enqueueUnsyncedLocalData() {
        val userId = sessionManager.getCurrentUserIdOnce() ?: return
        enqueueUnsyncedCategories(userId)
        enqueueUnsyncedSavingsGoals(userId)
        enqueueUnsyncedFinancialPlans(userId)
    }

    /**
     * Construit et enfile le payload `CREATE` pour chaque catégorie locale sans `syncId` — même
     * construction que `CategoryRepositoryImpl.enqueueCategorySync` (duplication assumée, voir la
     * doc de tête de cette classe sur la règle de trois). Le `syncId` généré est PERSISTÉ (`upsert`)
     * avant l'enfilage, contrairement au filet de sécurité de `deleteCategory` : cette ligne reste
     * active, un futur [pushBatch]/[pullEntityType] doit pouvoir la retrouver par ce même `syncId`.
     */
    private suspend fun enqueueUnsyncedCategories(userId: Long) {
        categoryDao.getUnsyncedForUser(userId).forEach { category ->
            val entity = category.copy(syncId = UUID.randomUUID().toString())
            categoryDao.upsert(entity)

            val payload = CategorySyncPayload(
                id = requireNotNull(entity.syncId),
                baseVersion = null,
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
                operation = SyncOperation.CREATE,
                payloadJson = json.encodeToString(CategorySyncPayload.serializer(), payload)
            )
        }
    }

    /** Voir [enqueueUnsyncedCategories] — même logique, appliquée à `savings_goals`. */
    private suspend fun enqueueUnsyncedSavingsGoals(userId: Long) {
        savingsGoalDao.getUnsyncedForUser(userId).forEach { goal ->
            val entity = goal.copy(syncId = UUID.randomUUID().toString())
            savingsGoalDao.upsert(entity)

            val payload = SavingsGoalSyncPayload(
                id = requireNotNull(entity.syncId),
                baseVersion = null,
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
                operation = SyncOperation.CREATE,
                payloadJson = json.encodeToString(SavingsGoalSyncPayload.serializer(), payload)
            )
        }
    }

    /** Voir [applyCategoryServerState] — même logique, appliquée à `financial_plans`. Les dépenses
     *  prévues (`financial_plan_items`) ne sont PAS touchées ici : elles ne sont pas synchronisées
     *  à cette étape (voir la KDoc de tête). */
    private suspend fun applyFinancialPlanServerState(state: FinancialPlanServerStateDto, allowCreate: Boolean) {
        val local = financialPlanDao.getBySyncId(state.id)
        if (local == null && !allowCreate) return
        val userId = local?.userId ?: sessionManager.getCurrentUserIdOnce() ?: return

        financialPlanDao.upsert(
            FinancialPlanEntity(
                id = local?.id ?: 0L,
                userId = userId,
                name = state.name,
                description = state.description,
                availableAmount = state.availableAmount,
                targetAmount = state.targetAmount,
                periodType = runCatching { PlanPeriodType.valueOf(state.periodType) }.getOrDefault(local?.periodType ?: PlanPeriodType.NONE),
                startDate = state.startDate,
                endDate = state.endDate,
                icon = runCatching { FinancialPlanIcon.valueOf(state.icon) }.getOrDefault(local?.icon ?: FinancialPlanIcon.WALLET),
                colorArgb = state.colorArgb,
                status = runCatching { PlanStatus.valueOf(state.status) }.getOrDefault(local?.status ?: PlanStatus.ACTIVE),
                createdAt = state.createdAt,
                syncId = state.id,
                updatedAt = state.updatedAt,
                deletedAt = state.deletedAt,
                version = state.version
            )
        )
    }

    /** Voir [enqueueUnsyncedCategories] — même logique, appliquée à `financial_plans`. */
    private suspend fun enqueueUnsyncedFinancialPlans(userId: Long) {
        financialPlanDao.getUnsyncedForUser(userId).forEach { plan ->
            val entity = plan.copy(syncId = UUID.randomUUID().toString())
            financialPlanDao.upsert(entity)

            val payload = FinancialPlanSyncPayload(
                id = requireNotNull(entity.syncId),
                baseVersion = null,
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
                operation = SyncOperation.CREATE,
                payloadJson = json.encodeToString(FinancialPlanSyncPayload.serializer(), payload)
            )
        }
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
        val SUPPORTED_ENTITY_TYPES = setOf("categories", "savings_goals", "financial_plans")
        const val MAX_ERROR_MESSAGE_LENGTH = 200
    }
}
