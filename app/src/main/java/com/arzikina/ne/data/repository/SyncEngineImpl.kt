package com.arzikina.ne.data.repository

import com.arzikina.ne.data.local.dao.AccountDao
import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.local.dao.FinancialPlanDao
import com.arzikina.ne.data.local.dao.PersonDao
import com.arzikina.ne.data.local.dao.SavingsGoalDao
import com.arzikina.ne.data.local.dao.SyncQueueDao
import com.arzikina.ne.data.local.entity.AccountEntity
import com.arzikina.ne.data.local.entity.CategoryEntity
import com.arzikina.ne.data.local.entity.FinancialPlanEntity
import com.arzikina.ne.data.local.entity.PersonEntity
import com.arzikina.ne.data.local.entity.SavingsGoalEntity
import com.arzikina.ne.data.local.entity.SyncQueueEntity
import com.arzikina.ne.data.remote.api.SyncApi
import com.arzikina.ne.data.remote.dto.AccountServerStateDto
import com.arzikina.ne.data.remote.dto.AccountSyncPayload
import com.arzikina.ne.data.remote.dto.CategoryServerStateDto
import com.arzikina.ne.data.remote.dto.CategorySyncPayload
import com.arzikina.ne.data.remote.dto.FinancialPlanServerStateDto
import com.arzikina.ne.data.remote.dto.FinancialPlanSyncPayload
import com.arzikina.ne.data.remote.dto.PersonServerStateDto
import com.arzikina.ne.data.remote.dto.PersonSyncPayload
import com.arzikina.ne.data.remote.dto.SavingsGoalServerStateDto
import com.arzikina.ne.data.remote.dto.SavingsGoalSyncPayload
import com.arzikina.ne.data.remote.dto.SyncPushOperationDto
import com.arzikina.ne.data.remote.dto.SyncPushRequestDto
import com.arzikina.ne.domain.model.AccountIcon
import com.arzikina.ne.domain.model.AccountType
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
 * `savings_goals`, `financial_plans`, `persons`, `accounts`) sont traitées en DUR ici — même choix
 * que côté serveur AVANT sa généralisation (étape 14, voir `entity_sync_configs.php`) : côté
 * Android, cette duplication reste volontairement ASSUMÉE (voir le plan validé de l'étape 14,
 * "Android reste dupliqué") — Room exige des `@Entity` concrets sans supertype commun sans
 * introduire une nouvelle couche d'abstraction, et la vérification à la compilation de Kotlin rend
 * cette duplication plus sûre ici qu'en PHP dynamique. Seule la boucle EXTERNE (drainage de la
 * file, pagination, curseur, transitions de statut) est déjà partagée entre les cinq — voir
 * [pushBatch]/[pullEntityType] — seules [applyCategoryServerState]/[applySavingsGoalServerState]/
 * [applyFinancialPlanServerState]/[applyPersonServerState]/[applyAccountServerState] (et la
 * construction du payload, faite en amont par chaque repository) diffèrent réellement par entité.
 *
 * Dépend directement de [CategoryDao]/[SavingsGoalDao]/[FinancialPlanDao]/[PersonDao]/[AccountDao]
 * (couche DATA vers couche DATA, jamais via leurs repositories respectifs, qui filtrent par
 * utilisateur COURANT et masquent volontairement `syncId`/`version` au domaine — voir leur KDoc) :
 * ce moteur doit pouvoir relire/écrire ces champs bruts, y compris sur des lignes déjà supprimées
 * (voir `getBySyncId` de chaque DAO).
 */
@Singleton
class SyncEngineImpl @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val categoryDao: CategoryDao,
    private val savingsGoalDao: SavingsGoalDao,
    private val financialPlanDao: FinancialPlanDao,
    private val personDao: PersonDao,
    private val accountDao: AccountDao,
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
     * Les entrées `FAILED` sont en plus filtrées par [isEligibleForRetry] : jamais un abandon
     * silencieux (voir docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md, section 8, "backoff exponentiel...
     * jamais abandonné silencieusement, juste espacé"), mais un délai croissant avec `retryCount`
     * avant chaque nouvelle tentative — protège une entrée en échec PERMANENT (donnée invalide qui
     * ne passera jamais côté serveur) de retenter à CHAQUE appel (manuel ou reconnexion réseau, qui
     * peut se déclencher plusieurs fois par minute sur une connexion instable, voir
     * `SyncConnectivityObserver`), sans jamais cesser complètement de réessayer.
     */
    override suspend fun pushPendingChanges(): SyncEngineResult {
        val now = System.currentTimeMillis()
        val eligibleFailedEntries = syncQueueDao.getByStatus(SyncStatus.FAILED).filter { isEligibleForRetry(it, now) }
        val pendingEntries = syncQueueDao.getByStatus(SyncStatus.PENDING) + eligibleFailedEntries
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
            "persons" ->
                applyPersonServerState(json.decodeFromJsonElement(PersonServerStateDto.serializer(), element), allowCreate)
            "accounts" ->
                applyAccountServerState(json.decodeFromJsonElement(AccountServerStateDto.serializer(), element), allowCreate)
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
        enqueueUnsyncedPersons(userId)
        enqueueUnsyncedAccounts(userId)
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

    /** Voir [applyCategoryServerState] — même logique, appliquée à `persons`. Les prêts/emprunts
     *  (`loans`) ne sont PAS touchés ici : ils ne sont pas synchronisés à cette étape (voir la KDoc
     *  de tête). */
    private suspend fun applyPersonServerState(state: PersonServerStateDto, allowCreate: Boolean) {
        val local = personDao.getBySyncId(state.id)
        if (local == null && !allowCreate) return
        val userId = local?.userId ?: sessionManager.getCurrentUserIdOnce() ?: return

        personDao.upsert(
            PersonEntity(
                id = local?.id ?: 0L,
                userId = userId,
                name = state.name,
                phone = state.phone,
                createdAt = state.createdAt,
                syncId = state.id,
                updatedAt = state.updatedAt,
                deletedAt = state.deletedAt,
                version = state.version
            )
        )
    }

    /** Voir [enqueueUnsyncedCategories] — même logique, appliquée à `persons`. */
    private suspend fun enqueueUnsyncedPersons(userId: Long) {
        personDao.getUnsyncedForUser(userId).forEach { person ->
            val entity = person.copy(syncId = UUID.randomUUID().toString())
            personDao.upsert(entity)

            val payload = PersonSyncPayload(
                id = requireNotNull(entity.syncId),
                baseVersion = null,
                name = entity.name,
                phone = entity.phone,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
            syncQueueEnqueuer.enqueue(
                entityType = "persons",
                entitySyncId = payload.id,
                operation = SyncOperation.CREATE,
                payloadJson = json.encodeToString(PersonSyncPayload.serializer(), payload)
            )
        }
    }

    /**
     * Voir [applyCategoryServerState] — même logique, appliquée à `accounts`.
     *
     * [AccountServerStateDto.isExcludedFromStatistics] : `Int` (`0`/`1`, PAS `Boolean` — voir la
     * KDoc de tête de `AccountSyncPayload.kt`) explicitement converti ici avec `!= 0`, seul endroit
     * où cette conversion a besoin d'exister.
     *
     * `CardSecretEntity` (numéro complet + CVV) n'est PAS touché ici : hors du champ de la
     * synchronisation (voir la KDoc de tête de `AccountSyncPayload.kt`) — un compte reçu par pull
     * sur un nouvel appareil n'a donc PAS de numéro/CVV enregistrés localement tant que l'utilisateur
     * ne les ressaisit pas sur CET appareil (`AccountRepository.saveCardSecrets`).
     */
    private suspend fun applyAccountServerState(state: AccountServerStateDto, allowCreate: Boolean) {
        val local = accountDao.getBySyncId(state.id)
        if (local == null && !allowCreate) return
        val userId = local?.userId ?: sessionManager.getCurrentUserIdOnce() ?: return

        accountDao.upsert(
            AccountEntity(
                id = local?.id ?: 0L,
                userId = userId,
                name = state.name,
                icon = runCatching { AccountIcon.valueOf(state.icon) }.getOrDefault(local?.icon ?: AccountIcon.CASH),
                colorArgb = state.colorArgb,
                currencyCode = state.currencyCode,
                initialBalanceMinor = state.initialBalanceMinor,
                createdAt = state.createdAt,
                type = runCatching { AccountType.valueOf(state.type) }.getOrDefault(local?.type ?: AccountType.CASH),
                cardLastFourDigits = state.cardLastFourDigits,
                cardExpiryMonth = state.cardExpiryMonth,
                cardExpiryYear = state.cardExpiryYear,
                isExcludedFromStatistics = state.isExcludedFromStatistics != 0,
                mobileMoneyPackageName = state.mobileMoneyPackageName,
                syncId = state.id,
                updatedAt = state.updatedAt,
                deletedAt = state.deletedAt,
                version = state.version
            )
        )
    }

    /** Voir [enqueueUnsyncedCategories] — même logique, appliquée à `accounts`. */
    private suspend fun enqueueUnsyncedAccounts(userId: Long) {
        accountDao.getUnsyncedForUser(userId).forEach { account ->
            val entity = account.copy(syncId = UUID.randomUUID().toString())
            accountDao.upsert(entity)

            val payload = AccountSyncPayload(
                id = requireNotNull(entity.syncId),
                baseVersion = null,
                name = entity.name,
                icon = entity.icon.name,
                colorArgb = entity.colorArgb,
                currencyCode = entity.currencyCode,
                initialBalanceMinor = entity.initialBalanceMinor,
                type = entity.type.name,
                cardLastFourDigits = entity.cardLastFourDigits,
                cardExpiryMonth = entity.cardExpiryMonth,
                cardExpiryYear = entity.cardExpiryYear,
                isExcludedFromStatistics = entity.isExcludedFromStatistics,
                mobileMoneyPackageName = entity.mobileMoneyPackageName,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
            syncQueueEnqueuer.enqueue(
                entityType = "accounts",
                entitySyncId = payload.id,
                operation = SyncOperation.CREATE,
                payloadJson = json.encodeToString(AccountSyncPayload.serializer(), payload)
            )
        }
    }

    /**
     * `true` si [entry] (déjà en `FAILED`) a suffisamment attendu depuis sa dernière tentative pour
     * être retentée maintenant — voir [RETRY_BACKOFF_MILLIS] pour la progression exacte.
     * `lastAttemptAt == null` (ne devrait pas arriver pour une entrée `FAILED`, [markFailed] le
     * renseigne systématiquement) : filet de sécurité, on retente plutôt que de bloquer
     * indéfiniment une entrée dans un état incohérent.
     */
    private fun isEligibleForRetry(entry: SyncQueueEntity, now: Long): Boolean {
        val lastAttempt = entry.lastAttemptAt ?: return true
        val backoffIndex = (entry.retryCount - 1).coerceIn(0, RETRY_BACKOFF_MILLIS.lastIndex)
        return now - lastAttempt >= RETRY_BACKOFF_MILLIS[backoffIndex]
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
        val SUPPORTED_ENTITY_TYPES = setOf("categories", "savings_goals", "financial_plans", "persons", "accounts")
        const val MAX_ERROR_MESSAGE_LENGTH = 200

        /** Délai minimal (ms) avant de retenter une entrée `FAILED`, indexé sur `retryCount - 1` —
         *  voir [isEligibleForRetry]. Progression reprise telle quelle de
         *  docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md (section 8) : 30s, 1min, 5min, 30min, puis 1h en
         *  continu (dernier élément réutilisé indéfiniment, jamais de coupure définitive). */
        val RETRY_BACKOFF_MILLIS = listOf(
            30_000L,
            60_000L,
            5 * 60_000L,
            30 * 60_000L,
            60 * 60_000L
        )
    }
}
