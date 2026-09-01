package com.arzikina.ne.data.repository

import com.arzikina.ne.data.local.dao.AccountDao
import com.arzikina.ne.data.local.dao.BudgetDao
import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.local.dao.FinancialPlanDao
import com.arzikina.ne.data.local.dao.FinancialPlanItemDao
import com.arzikina.ne.data.local.dao.LoanDao
import com.arzikina.ne.data.local.dao.LoanPaymentDao
import com.arzikina.ne.data.local.dao.PersonDao
import com.arzikina.ne.data.local.dao.RecurringTransactionDao
import com.arzikina.ne.data.local.dao.RecurringTransactionOccurrenceDao
import com.arzikina.ne.data.local.dao.SavingsGoalDao
import com.arzikina.ne.data.local.dao.SyncQueueDao
import com.arzikina.ne.data.local.dao.TransactionDao
import com.arzikina.ne.data.local.dao.UserPreferencesDao
import com.arzikina.ne.data.local.entity.AccountEntity
import com.arzikina.ne.data.local.entity.BudgetEntity
import com.arzikina.ne.data.local.entity.CategoryEntity
import com.arzikina.ne.data.local.entity.FinancialPlanEntity
import com.arzikina.ne.data.local.entity.FinancialPlanItemEntity
import com.arzikina.ne.data.local.entity.LoanEntity
import com.arzikina.ne.data.local.entity.LoanPaymentEntity
import com.arzikina.ne.data.local.entity.PersonEntity
import com.arzikina.ne.data.local.entity.RecurringTransactionEntity
import com.arzikina.ne.data.local.entity.RecurringTransactionOccurrenceEntity
import com.arzikina.ne.data.local.entity.SavingsGoalEntity
import com.arzikina.ne.data.local.entity.SyncQueueEntity
import com.arzikina.ne.data.local.entity.TransactionEntity
import com.arzikina.ne.data.local.entity.UserPreferencesEntity
import com.arzikina.ne.data.remote.api.SyncApi
import com.arzikina.ne.data.remote.dto.AccountServerStateDto
import com.arzikina.ne.data.remote.dto.AccountSyncPayload
import com.arzikina.ne.data.remote.dto.BudgetServerStateDto
import com.arzikina.ne.data.remote.dto.BudgetSyncPayload
import com.arzikina.ne.data.remote.dto.CategoryServerStateDto
import com.arzikina.ne.data.remote.dto.CategorySyncPayload
import com.arzikina.ne.data.remote.dto.FinancialPlanItemServerStateDto
import com.arzikina.ne.data.remote.dto.FinancialPlanItemSyncPayload
import com.arzikina.ne.data.remote.dto.FinancialPlanServerStateDto
import com.arzikina.ne.data.remote.dto.FinancialPlanSyncPayload
import com.arzikina.ne.data.remote.dto.LoanPaymentServerStateDto
import com.arzikina.ne.data.remote.dto.LoanServerStateDto
import com.arzikina.ne.data.remote.dto.PersonServerStateDto
import com.arzikina.ne.data.remote.dto.PersonSyncPayload
import com.arzikina.ne.data.remote.dto.RecurringTransactionOccurrenceServerStateDto
import com.arzikina.ne.data.remote.dto.RecurringTransactionOccurrenceSyncPayload
import com.arzikina.ne.data.remote.dto.RecurringTransactionServerStateDto
import com.arzikina.ne.data.remote.dto.RecurringTransactionSyncPayload
import com.arzikina.ne.data.remote.dto.SavingsGoalServerStateDto
import com.arzikina.ne.data.remote.dto.SavingsGoalSyncPayload
import com.arzikina.ne.data.remote.dto.SyncPushOperationDto
import com.arzikina.ne.data.remote.dto.SyncPushRequestDto
import com.arzikina.ne.data.remote.dto.TransactionServerStateDto
import com.arzikina.ne.data.remote.dto.UserPreferencesServerStateDto
import com.arzikina.ne.data.remote.dto.UserPreferencesSyncPayload
import com.arzikina.ne.domain.model.AccountIcon
import com.arzikina.ne.domain.model.AccountType
import com.arzikina.ne.domain.model.BudgetPeriod
import com.arzikina.ne.domain.model.CategoryIcon
import com.arzikina.ne.domain.model.FeeType
import com.arzikina.ne.domain.model.FinancialPlanIcon
import com.arzikina.ne.domain.model.LoanReason
import com.arzikina.ne.domain.model.LoanStatus
import com.arzikina.ne.domain.model.LoanType
import com.arzikina.ne.domain.model.OccurrenceStatus
import com.arzikina.ne.domain.model.PaymentMethod
import com.arzikina.ne.domain.model.PlanItemPriority
import com.arzikina.ne.domain.model.PlanItemStatus
import com.arzikina.ne.domain.model.PlanPeriodType
import com.arzikina.ne.domain.model.PlanStatus
import com.arzikina.ne.domain.model.RecurringFrequency
import com.arzikina.ne.domain.model.RepaymentMode
import com.arzikina.ne.domain.model.SyncEngineResult
import com.arzikina.ne.domain.model.SyncOperation
import com.arzikina.ne.domain.model.SyncPullResult
import com.arzikina.ne.domain.model.SyncQueueStatus
import com.arzikina.ne.domain.model.SyncStatus
import com.arzikina.ne.domain.model.ThemeMode
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.SyncEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Voir [SyncEngine] pour le contrat et l'étape actuelle. [SUPPORTED_ENTITY_TYPES] (`categories`,
 * `savings_goals`, `financial_plans`, `persons`, `accounts`, `transactions`) sont traitées en DUR
 * ici — même choix que côté serveur AVANT sa généralisation (étape 14, voir `entity_sync_configs.php`) :
 * côté Android, cette duplication reste volontairement ASSUMÉE (voir le plan validé de l'étape 14,
 * "Android reste dupliqué") — Room exige des `@Entity` concrets sans supertype commun sans
 * introduire une nouvelle couche d'abstraction, et la vérification à la compilation de Kotlin rend
 * cette duplication plus sûre ici qu'en PHP dynamique. Seule la boucle EXTERNE (drainage de la
 * file, pagination, curseur, transitions de statut) est déjà partagée entre les six — voir
 * [pushBatch]/[pullEntityType] — seules [applyCategoryServerState]/[applySavingsGoalServerState]/
 * [applyFinancialPlanServerState]/[applyPersonServerState]/[applyAccountServerState]/
 * [applyTransactionServerState] (et la construction du payload, faite en amont par chaque
 * repository, ou par [TransactionSyncEnqueuer] pour `transactions` — voir sa KDoc) diffèrent
 * réellement par entité.
 *
 * Dépend directement de [CategoryDao]/[SavingsGoalDao]/[FinancialPlanDao]/[PersonDao]/[AccountDao]/
 * [TransactionDao] (couche DATA vers couche DATA, jamais via leurs repositories respectifs, qui
 * filtrent par utilisateur COURANT et masquent volontairement `syncId`/`version` au domaine — voir
 * leur KDoc) : ce moteur doit pouvoir relire/écrire ces champs bruts, y compris sur des lignes déjà
 * supprimées (voir `getBySyncId` de chaque DAO).
 */
@Singleton
class SyncEngineImpl @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val categoryDao: CategoryDao,
    private val savingsGoalDao: SavingsGoalDao,
    private val financialPlanDao: FinancialPlanDao,
    private val personDao: PersonDao,
    private val accountDao: AccountDao,
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao,
    private val loanDao: LoanDao,
    private val loanPaymentDao: LoanPaymentDao,
    private val recurringTransactionDao: RecurringTransactionDao,
    private val occurrenceDao: RecurringTransactionOccurrenceDao,
    private val financialPlanItemDao: FinancialPlanItemDao,
    private val userPreferencesDao: UserPreferencesDao,
    private val syncApi: SyncApi,
    private val syncCursorStore: SyncCursorStore,
    private val syncQueueEnqueuer: SyncQueueEnqueuer,
    private val transactionSyncEnqueuer: TransactionSyncEnqueuer,
    private val loanSyncEnqueuer: LoanSyncEnqueuer,
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

        // Bloc volontairement large (construction de la requête ET appel réseau ET décodage de la
        // réponse) sous UN SEUL `catch (e: Exception)` — pas seulement `IOException` comme avant.
        // Bug réel rencontré en pratique (voir docs/sync/SCENARIOS-DE-TEST.md, addendum
        // "SYNCING bloqué") : `SyncApi.push` peut lever une `SerializationException` (réponse
        // serveur non-JSON valide, ex. avertissement PHP mélangé au corps JSON — situation vécue
        // pendant le déboguage de `entity_sync_configs.php`/étape 17.4), qui N'HÉRITE PAS de
        // `IOException` et n'était donc jamais rattrapée : les entrées restaient marquées `SYNCING`
        // pour toujours (exclues de [isEligibleForRetry], qui ne relit que `PENDING`/`FAILED`) —
        // plus jamais retentées, indicateur "Synchronisation…" bloqué indéfiniment côté
        // `SettingsViewModel.syncIndicatorState`. Toute exception ICI, quelle que soit sa nature,
        // doit désormais faire repasser le lot en `FAILED` : jamais un abandon silencieux.
        val response = try {
            val request = SyncPushRequestDto(
                entityType = entityType,
                operations = entries.map { entry ->
                    SyncPushOperationDto(operation = entry.operation.name, entity = json.parseToJsonElement(entry.payloadJson))
                }
            )
            syncApi.push(request)
        } catch (e: CancellationException) {
            // JAMAIS avalée par le `catch (e: Exception)` ci-dessous : une annulation de coroutine
            // (écran fermé pendant le push, `viewModelScope` détruit) doit continuer à se propager
            // normalement, pas être traitée comme un échec métier — sinon `markFailed` s'exécuterait
            // sur un lot potentiellement déjà repris par une AUTRE tentative de synchronisation, et
            // la coroutine annulée resterait active plus longtemps que prévu (violerait la
            // "structured concurrency" de Kotlin, voir la documentation officielle de
            // `CancellationException`).
            throw e
        } catch (e: Exception) {
            // Échec réseau/serveur/décodage global (voir la KDoc de SyncStatus) : toutes les
            // entrées du lot repassent FAILED, aucune n'est perdue — une future planification
            // (WorkManager) ou un nouveau tap sur "Synchroniser maintenant" les reprendra.
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
     *
     * `catch (e: Exception)` (PAS seulement `IOException` comme avant) autour de l'appel réseau —
     * bug réel trouvé en pratique après le câblage de `user_preferences` (étape 22.5), SYMÉTRIQUE à
     * celui déjà corrigé côté push à l'étape 17.6 (voir la KDoc de tête de [pushBatch]) mais jamais
     * appliqué ici : une réponse serveur non conforme pour UN SEUL type d'entité (ex. table/entrée
     * `entity_sync_configs.php` pas encore déployée côté serveur) levait une `SerializationException`,
     * jamais rattrapée — elle remontait alors à travers [pullRemoteChanges] (dont la boucle sur
     * [SUPPORTED_ENTITY_TYPES] n'a elle-même aucun `try/catch`) jusqu'à l'appelant
     * (`SettingsViewModel.syncNow`), interrompant la synchronisation de TOUTES les entités, pas
     * seulement celle en cause — indicateur "Synchronisation impossible, réessaie plus tard" bloquant
     * même les entités par ailleurs saines. `catch (e: CancellationException) { throw e }` avant, pour
     * ne pas casser l'annulation de coroutine (même raisonnement que [pushBatch]).
     */
    private suspend fun pullEntityType(entityType: String): Pair<Int, Int> {
        var received = 0
        var applied = 0
        var cursor = syncCursorStore.getLastPulledAt(entityType)

        while (true) {
            val response = try {
                syncApi.pull(entityType, cursor)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                break // Échec réseau/serveur/décodage : le curseur n'a pas avancé, rien n'est perdu.
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
            "budgets" ->
                applyBudgetServerState(json.decodeFromJsonElement(BudgetServerStateDto.serializer(), element), allowCreate)
            "transactions" ->
                applyTransactionServerState(json.decodeFromJsonElement(TransactionServerStateDto.serializer(), element), allowCreate)
            "loans" ->
                applyLoanServerState(json.decodeFromJsonElement(LoanServerStateDto.serializer(), element), allowCreate)
            "loan_payments" ->
                applyLoanPaymentServerState(json.decodeFromJsonElement(LoanPaymentServerStateDto.serializer(), element), allowCreate)
            "recurring_transactions" ->
                applyRecurringTransactionServerState(json.decodeFromJsonElement(RecurringTransactionServerStateDto.serializer(), element), allowCreate)
            "recurring_transaction_occurrences" ->
                applyRecurringTransactionOccurrenceServerState(json.decodeFromJsonElement(RecurringTransactionOccurrenceServerStateDto.serializer(), element), allowCreate)
            "financial_plan_items" ->
                applyFinancialPlanItemServerState(json.decodeFromJsonElement(FinancialPlanItemServerStateDto.serializer(), element), allowCreate)
            "user_preferences" ->
                applyUserPreferencesServerState(json.decodeFromJsonElement(UserPreferencesServerStateDto.serializer(), element), allowCreate)
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
     *
     * RATTACHEMENT ANTI-DOUBLON (voir [CategoryDao.getUnsyncedByNameAndType]) : quand `syncId` est
     * inconnu localement ET qu'un pull est en cours, on cherche d'abord une catégorie locale PAS
     * ENCORE synchronisée (`syncId IS NULL`) de même nom/type avant d'en créer une nouvelle — cause
     * racine des catégories système en double (Alimentation, Transport...) semées indépendamment
     * par [com.arzikina.ne.data.local.database.NewUserDefaultDataSeeder] sur chaque appareil/
     * réinstallation : sans ce rattachement, chacune recevait un `syncId` distinct et restait à
     * jamais une ligne séparée aux yeux de ce moteur. Portée volontairement étroite
     * (`syncId IS NULL` uniquement) : une catégorie déjà synchronisée, quel que soit son nom, n'est
     * JAMAIS fusionnée avec une autre — seul un doublon encore "orphelin" peut être rattaché.
     */
    private suspend fun applyCategoryServerState(state: CategoryServerStateDto, allowCreate: Boolean) {
        var local = categoryDao.getBySyncId(state.id)
        val userId = local?.userId ?: sessionManager.getCurrentUserIdOnce() ?: return
        if (local == null) {
            if (!allowCreate) return
            val type = runCatching { TransactionType.valueOf(state.type) }.getOrNull()
            if (type != null) {
                local = categoryDao.getUnsyncedByNameAndType(userId, state.name, type)
            }
        }

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
        // Après `categories` (dont [enqueueUnsyncedBudgets] dépend, voir sa KDoc) — même
        // raisonnement d'ordre que `transactions` en toute fin de cette liste, en plus local.
        enqueueUnsyncedBudgets(userId)
        enqueueUnsyncedSavingsGoals(userId)
        enqueueUnsyncedFinancialPlans(userId)
        enqueueUnsyncedPersons(userId)
        enqueueUnsyncedAccounts(userId)
        // TOUJOURS EN DERNIER (voir la KDoc de tête de [TransactionSyncEnqueuer]) : une transaction
        // référence un compte/une catégorie par leur `syncId` — celui-ci doit déjà être PERSISTÉ (et
        // idéalement déjà enfilé) au moment où la transaction l'est à son tour, sans quoi
        // [TransactionSyncEnqueuer] devrait recourir à son filet de sécurité (génération d'un
        // `syncId` non lui-même enfilé, voir sa KDoc) au lieu du cas normal ci-dessus.
        enqueueUnsyncedTransactions(userId)
        // ENCORE APRÈS `transactions` (voir la KDoc de tête de [LoanSyncEnqueuer]) : un prêt/emprunt
        // référence sa transaction de décaissement, un remboursement référence son prêt parent ET sa
        // propre transaction — les deux doivent déjà être connus au moment de cet enfilage.
        enqueueUnsyncedLoans(userId)
        enqueueUnsyncedLoanPayments(userId)
        // ENCORE APRÈS `accounts`/`categories` (référencées directement) ET `transactions`
        // (référencée par les occurrences déjà ACCEPTED/MODIFIED) — voir la KDoc de tête de
        // `RecurringTransactionSyncPayload.kt`. `recurring_transactions` avant ses propres
        // occurrences : une occurrence référence toujours sa règle parente.
        enqueueUnsyncedRecurringTransactions(userId)
        enqueueUnsyncedRecurringTransactionOccurrences(userId)
        // ENCORE APRÈS `financial_plans`/`categories`/`transactions` (les trois référencées, la
        // dernière nullable, voir la KDoc de tête de `FinancialPlanItemSyncPayload.kt`) — même
        // raisonnement que `recurring_transaction_occurrences` ci-dessus.
        enqueueUnsyncedFinancialPlanItems(userId)
        // AUCUNE contrainte d'ordre (voir la KDoc de tête de [applyUserPreferencesServerState]) :
        // seule entité de ce registre sans la moindre référence croisée, sa place ici est arbitraire.
        enqueueUnsyncedUserPreferences(userId)
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
        var local = accountDao.getBySyncId(state.id)
        val userId = local?.userId ?: sessionManager.getCurrentUserIdOnce() ?: return
        if (local == null) {
            if (!allowCreate) return
            // Rattachement anti-doublon — voir la KDoc de [applyCategoryServerState] (même
            // raisonnement, appliqué ici à `DefaultAccounts` : "Espèces"/"Banque"/... semés à
            // l'inscription sur chaque appareil).
            val type = runCatching { AccountType.valueOf(state.type) }.getOrNull()
            if (type != null) {
                local = accountDao.getUnsyncedByNameAndType(userId, state.name, type)
            }
        }

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
     * DIFFÉRENT de [applyCategoryServerState] et consorts : `Budget` référence une catégorie par son
     * `syncId`, jamais par un `id` Room local (voir la KDoc de tête de `BudgetSyncPayload.kt`).
     * Résolution INVERSE ici — `categorySyncId` → `categoryId` local, via `categoryDao.getBySyncId`
     * — même principe que [applyTransactionServerState] ci-dessous, en plus simple (une seule
     * référence, jamais nulle).
     *
     * [state.categorySyncId] introuvable localement (catégorie pas encore connue sur CET appareil) :
     * ligne ignorée silencieusement (`?: return`), comme n'importe quelle entrée malformée (voir la
     * KDoc de [pullEntityType]) — ne devrait quasiment jamais arriver grâce à l'ordre de
     * [SUPPORTED_ENTITY_TYPES] (`budgets` toujours APRÈS `categories`).
     */
    private suspend fun applyBudgetServerState(state: BudgetServerStateDto, allowCreate: Boolean) {
        val local = budgetDao.getBySyncId(state.id)
        if (local == null && !allowCreate) return
        val userId = local?.userId ?: sessionManager.getCurrentUserIdOnce() ?: return
        val categoryId = categoryDao.getBySyncId(state.categorySyncId)?.id ?: return

        budgetDao.upsert(
            BudgetEntity(
                id = local?.id ?: 0L,
                userId = userId,
                categoryId = categoryId,
                period = runCatching { BudgetPeriod.valueOf(state.period) }.getOrDefault(local?.period ?: BudgetPeriod.MONTHLY),
                limitAmount = state.limitAmount,
                currencyCode = state.currencyCode,
                createdAt = state.createdAt,
                startDate = state.startDate,
                endDate = state.endDate,
                syncId = state.id,
                updatedAt = state.updatedAt,
                deletedAt = state.deletedAt,
                version = state.version
            )
        )
    }

    /**
     * Voir [enqueueUnsyncedCategories] pour le principe général. La résolution `categoryId` →
     * `categorySyncId` (filet de sécurité inclus) est DUPLIQUÉE ici plutôt que déléguée à une classe
     * partagée type `TransactionSyncEnqueuer` — même raisonnement que
     * `BudgetRepositoryImpl.resolveCategorySyncId` (un seul appelant de chaque côté : ici pour le
     * backfill, là-bas pour l'écriture courante ; une abstraction commune n'apporterait rien).
     */
    private suspend fun enqueueUnsyncedBudgets(userId: Long) {
        budgetDao.getUnsyncedForUser(userId).forEach { budget ->
            val entity = budget.copy(syncId = UUID.randomUUID().toString())
            budgetDao.upsert(entity)

            val category = categoryDao.getById(entity.categoryId, userId) ?: return@forEach
            val categorySyncId = category.syncId ?: UUID.randomUUID().toString().also { newSyncId ->
                categoryDao.upsert(category.copy(syncId = newSyncId))
            }

            val payload = BudgetSyncPayload(
                id = requireNotNull(entity.syncId),
                baseVersion = null,
                categorySyncId = categorySyncId,
                period = entity.period.name,
                limitAmount = entity.limitAmount,
                currencyCode = entity.currencyCode,
                startDate = entity.startDate,
                endDate = entity.endDate,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
            syncQueueEnqueuer.enqueue(
                entityType = "budgets",
                entitySyncId = payload.id,
                operation = SyncOperation.CREATE,
                payloadJson = json.encodeToString(BudgetSyncPayload.serializer(), payload)
            )
        }
    }

    /**
     * DIFFÉRENT de toutes les fonctions `applyXServerState` précédentes : `Transaction` référence
     * d'autres entités synchronisées par leur `syncId`, jamais par un `id` Room local (voir la KDoc
     * de tête de `TransactionSyncPayload.kt`). Résolution INVERSE ici — `accountSyncId` →
     * `accountId` local, etc., via `getBySyncId` de chaque DAO concerné.
     *
     * [state.accountSyncId] introuvable localement (compte pas encore connu sur CET appareil) :
     * ligne ignorée silencieusement, comme n'importe quelle entrée malformée (voir la KDoc de
     * [pullEntityType]) — ne devrait quasiment jamais arriver grâce à l'ordre de
     * [SUPPORTED_ENTITY_TYPES] (`transactions` toujours EN DERNIER, voir sa KDoc), qui garantit que
     * les comptes/catégories référencés ont déjà été reçus (pull) ou confirmés (push) AVANT cette
     * transaction.
     *
     * [TransactionEntity.receiptPhotoUri]/[TransactionEntity.receiptId] : JAMAIS renseignés par le
     * serveur (hors du champ de la synchronisation, voir la KDoc de tête de
     * `TransactionSyncPayload.kt`) — la valeur LOCALE existante est préservée ([local]), `null` pour
     * une transaction totalement nouvelle sur cet appareil.
     */
    private suspend fun applyTransactionServerState(state: TransactionServerStateDto, allowCreate: Boolean) {
        val local = transactionDao.getBySyncId(state.id)
        if (local == null && !allowCreate) return
        val userId = local?.userId ?: sessionManager.getCurrentUserIdOnce() ?: return

        val accountId = accountDao.getBySyncId(state.accountSyncId)?.id ?: return
        val transferAccountId = state.transferAccountSyncId?.let { accountDao.getBySyncId(it)?.id }
        val categoryId = state.categorySyncId?.let { categoryDao.getBySyncId(it)?.id }
        val feeTransactionId = state.feeTransactionSyncId?.let { transactionDao.getBySyncId(it)?.id }

        transactionDao.upsert(
            TransactionEntity(
                id = local?.id ?: 0L,
                userId = userId,
                amount = state.amount,
                type = runCatching { TransactionType.valueOf(state.type) }.getOrDefault(local?.type ?: TransactionType.EXPENSE),
                accountId = accountId,
                transferAccountId = transferAccountId,
                categoryId = categoryId,
                date = state.date,
                description = state.description,
                receiptPhotoUri = local?.receiptPhotoUri,
                latitude = state.latitude,
                longitude = state.longitude,
                paymentMethod = state.paymentMethod?.let { runCatching { PaymentMethod.valueOf(it) }.getOrNull() },
                createdAt = state.createdAt,
                feeTransactionId = feeTransactionId,
                feeType = state.feeType?.let { runCatching { FeeType.valueOf(it) }.getOrNull() },
                receiptId = local?.receiptId,
                syncId = state.id,
                updatedAt = state.updatedAt,
                deletedAt = state.deletedAt,
                version = state.version
            )
        )
    }

    /** Voir [enqueueUnsyncedCategories] pour le principe général — PLUS SIMPLE ici : la construction
     *  du payload (résolution `id` → `syncId`, filet de sécurité inclus) est déjà entièrement prise
     *  en charge par [TransactionSyncEnqueuer] (voir sa KDoc de tête), jamais dupliquée ici. */
    private suspend fun enqueueUnsyncedTransactions(userId: Long) {
        transactionDao.getUnsyncedForUser(userId).forEach { transaction ->
            val entity = transaction.copy(syncId = UUID.randomUUID().toString())
            transactionDao.upsert(entity)
            transactionSyncEnqueuer.enqueue(entity, SyncOperation.CREATE)
        }
    }

    /**
     * DIFFÉRENT de toutes les fonctions `applyXServerState` précédentes (sauf
     * [applyTransactionServerState]) : `Loan` référence TROIS autres entités synchronisées par leur
     * `syncId` (personne, compte, transaction de décaissement) — voir la KDoc de tête de
     * `LoanSyncPayload.kt`. Résolution INVERSE ici, via `getBySyncId` de chaque DAO concerné.
     *
     * Toutes ces références sont NON NULLABLES (contrairement à `Transaction.categorySyncId`) :
     * l'une d'elles introuvable localement (pas encore connue sur CET appareil) → ligne ignorée
     * silencieusement (`?: return`), comme n'importe quelle entrée malformée (voir la KDoc de
     * [pullEntityType]) — ne devrait quasiment jamais arriver grâce à l'ordre de
     * [SUPPORTED_ENTITY_TYPES] (`loans` toujours APRÈS `persons`/`accounts`/`transactions`).
     */
    private suspend fun applyLoanServerState(state: LoanServerStateDto, allowCreate: Boolean) {
        val local = loanDao.getBySyncId(state.id)
        if (local == null && !allowCreate) return
        val userId = local?.userId ?: sessionManager.getCurrentUserIdOnce() ?: return
        val personId = personDao.getBySyncId(state.personSyncId)?.id ?: return
        val accountId = accountDao.getBySyncId(state.accountSyncId)?.id ?: return
        val transactionId = transactionDao.getBySyncId(state.transactionSyncId)?.id ?: return

        loanDao.upsert(
            LoanEntity(
                id = local?.id ?: 0L,
                userId = userId,
                personId = personId,
                accountId = accountId,
                type = runCatching { LoanType.valueOf(state.type) }.getOrDefault(local?.type ?: LoanType.LENT),
                amount = state.amount,
                amountRepaid = state.amountRepaid,
                remainingAmount = state.remainingAmount,
                startDate = state.startDate,
                dueDate = state.dueDate,
                reason = runCatching { LoanReason.valueOf(state.reason) }.getOrDefault(local?.reason ?: LoanReason.OTHER),
                reasonCustomText = state.reasonCustomText,
                repaymentMode = runCatching { RepaymentMode.valueOf(state.repaymentMode) }.getOrDefault(local?.repaymentMode ?: RepaymentMode.CUSTOM),
                description = state.description,
                status = runCatching { LoanStatus.valueOf(state.status) }.getOrDefault(local?.status ?: LoanStatus.ONGOING),
                createdAt = state.createdAt,
                updatedAt = state.updatedAt,
                transactionId = transactionId,
                syncId = state.id,
                deletedAt = state.deletedAt,
                version = state.version
            )
        )
    }

    /** Voir [enqueueUnsyncedCategories] pour le principe général — PLUS SIMPLE ici : la construction
     *  du payload (résolution `id` → `syncId`, filet de sécurité inclus) est déjà entièrement prise
     *  en charge par [LoanSyncEnqueuer] (voir sa KDoc de tête), jamais dupliquée ici. */
    private suspend fun enqueueUnsyncedLoans(userId: Long) {
        loanDao.getUnsyncedForUser(userId).forEach { loan ->
            val entity = loan.copy(syncId = UUID.randomUUID().toString())
            loanDao.upsert(entity)
            loanSyncEnqueuer.enqueueLoan(entity, SyncOperation.CREATE)
        }
    }

    /** Voir [applyLoanServerState] — même logique, appliquée à `loan_payments` (référence le prêt
     *  parent, un compte, et sa propre transaction de remboursement). */
    private suspend fun applyLoanPaymentServerState(state: LoanPaymentServerStateDto, allowCreate: Boolean) {
        val local = loanPaymentDao.getBySyncId(state.id)
        if (local == null && !allowCreate) return
        val userId = local?.userId ?: sessionManager.getCurrentUserIdOnce() ?: return
        val loanId = loanDao.getBySyncId(state.loanSyncId)?.id ?: return
        val accountId = accountDao.getBySyncId(state.accountSyncId)?.id ?: return
        val transactionId = transactionDao.getBySyncId(state.transactionSyncId)?.id ?: return

        loanPaymentDao.upsert(
            LoanPaymentEntity(
                id = local?.id ?: 0L,
                userId = userId,
                loanId = loanId,
                accountId = accountId,
                amount = state.amount,
                date = state.date,
                note = state.note,
                transactionId = transactionId,
                createdAt = state.createdAt,
                syncId = state.id,
                updatedAt = state.updatedAt,
                deletedAt = state.deletedAt,
                version = state.version
            )
        )
    }

    /** Voir [enqueueUnsyncedLoans] — même logique, appliquée à `loan_payments`. */
    private suspend fun enqueueUnsyncedLoanPayments(userId: Long) {
        loanPaymentDao.getUnsyncedForUser(userId).forEach { payment ->
            val entity = payment.copy(syncId = UUID.randomUUID().toString())
            loanPaymentDao.upsert(entity)
            loanSyncEnqueuer.enqueueLoanPayment(entity, SyncOperation.CREATE)
        }
    }

    /**
     * DIFFÉRENT de [applyBudgetServerState] : `accountSyncId` n'est JAMAIS nul (comme
     * `Transaction.accountSyncId`), `categorySyncId` PEUT l'être (comme `Transaction.categorySyncId`,
     * voir sa KDoc) — même raisonnement double ici, voir la KDoc de tête de
     * `RecurringTransactionSyncPayload.kt`.
     *
     * [state.accountSyncId] introuvable localement : ligne ignorée silencieusement (`?: return`),
     * comme n'importe quelle entrée malformée (voir la KDoc de [pullEntityType]) — ne devrait
     * quasiment jamais arriver grâce à l'ordre de [SUPPORTED_ENTITY_TYPES] (`recurring_transactions`
     * toujours APRÈS `accounts`/`categories`).
     *
     * [state.isActive] : `Int` (`0`/`1`), pas `Boolean` — voir la KDoc de tête de
     * [RecurringTransactionServerStateDto] (même raisonnement que
     * `AccountServerStateDto.isExcludedFromStatistics`).
     */
    private suspend fun applyRecurringTransactionServerState(state: RecurringTransactionServerStateDto, allowCreate: Boolean) {
        val local = recurringTransactionDao.getBySyncId(state.id)
        if (local == null && !allowCreate) return
        val userId = local?.userId ?: sessionManager.getCurrentUserIdOnce() ?: return
        val accountId = accountDao.getBySyncId(state.accountSyncId)?.id ?: return
        val categoryId = state.categorySyncId?.let { categoryDao.getBySyncId(it)?.id }

        recurringTransactionDao.upsert(
            RecurringTransactionEntity(
                id = local?.id ?: 0L,
                userId = userId,
                type = runCatching { TransactionType.valueOf(state.type) }.getOrDefault(local?.type ?: TransactionType.EXPENSE),
                amount = state.amount,
                accountId = accountId,
                categoryId = categoryId,
                description = state.description,
                paymentMethod = state.paymentMethod?.let { runCatching { PaymentMethod.valueOf(it) }.getOrNull() },
                startDate = state.startDate,
                endDate = state.endDate,
                frequency = runCatching { RecurringFrequency.valueOf(state.frequency) }.getOrDefault(local?.frequency ?: RecurringFrequency.ONCE),
                nextExecutionDate = state.nextExecutionDate,
                isActive = state.isActive != 0,
                createdAt = state.createdAt,
                updatedAt = state.updatedAt,
                triggerHour = state.triggerHour,
                triggerMinute = state.triggerMinute,
                syncId = state.id,
                deletedAt = state.deletedAt,
                version = state.version
            )
        )
    }

    /**
     * Voir [enqueueUnsyncedBudgets] pour le principe général — même raisonnement pour la résolution
     * `accountId`/`categoryId` → `accountSyncId`/`categorySyncId` (filet de sécurité inclus),
     * DUPLIQUÉE ici plutôt que déléguée à une classe partagée (un seul appelant : voir la KDoc de
     * tête de `RecurringTransactionSyncPayload.kt`, "pas de classe partagée type LoanSyncEnqueuer").
     * `categoryId` reste `null` possible (voir [applyRecurringTransactionServerState]) : ligne
     * ignorée silencieusement (`?: return@forEach`) UNIQUEMENT si le COMPTE (jamais nul) est
     * introuvable — jamais pour la catégorie, optionnelle.
     */
    private suspend fun enqueueUnsyncedRecurringTransactions(userId: Long) {
        recurringTransactionDao.getUnsyncedForUser(userId).forEach { rule ->
            val entity = rule.copy(syncId = UUID.randomUUID().toString())
            recurringTransactionDao.upsert(entity)

            val account = accountDao.getById(entity.accountId, userId) ?: return@forEach
            val accountSyncId = account.syncId ?: UUID.randomUUID().toString().also { newSyncId ->
                accountDao.upsert(account.copy(syncId = newSyncId))
            }
            val categorySyncId = entity.categoryId?.let { categoryId ->
                val category = categoryDao.getById(categoryId, userId) ?: return@let null
                category.syncId ?: UUID.randomUUID().toString().also { newSyncId ->
                    categoryDao.upsert(category.copy(syncId = newSyncId))
                }
            }

            val payload = RecurringTransactionSyncPayload(
                id = requireNotNull(entity.syncId),
                baseVersion = null,
                type = entity.type.name,
                amount = entity.amount,
                accountSyncId = accountSyncId,
                categorySyncId = categorySyncId,
                description = entity.description,
                paymentMethod = entity.paymentMethod?.name,
                startDate = entity.startDate,
                endDate = entity.endDate,
                frequency = entity.frequency.name,
                nextExecutionDate = entity.nextExecutionDate,
                isActive = entity.isActive,
                triggerHour = entity.triggerHour,
                triggerMinute = entity.triggerMinute,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
            syncQueueEnqueuer.enqueue(
                entityType = "recurring_transactions",
                entitySyncId = payload.id,
                operation = SyncOperation.CREATE,
                payloadJson = json.encodeToString(RecurringTransactionSyncPayload.serializer(), payload)
            )
        }
    }

    /**
     * Voir [applyLoanServerState] pour le principe général (référence NON nullable +
     * référence nullable) — appliqué ici à `recurring_transaction_occurrences` :
     * [state.recurringTransactionSyncId] (la règle parente) n'est jamais nul,
     * [state.transactionSyncId] ne l'est que pour une occurrence [OccurrenceStatus.ACCEPTED]/
     * [OccurrenceStatus.MODIFIED] (voir la KDoc de tête de
     * `RecurringTransactionOccurrenceEntity`/`RecurringTransactionSyncPayload.kt`).
     */
    private suspend fun applyRecurringTransactionOccurrenceServerState(
        state: RecurringTransactionOccurrenceServerStateDto,
        allowCreate: Boolean
    ) {
        val local = occurrenceDao.getBySyncId(state.id)
        if (local == null && !allowCreate) return
        val userId = local?.userId ?: sessionManager.getCurrentUserIdOnce() ?: return
        val recurringTransactionId = recurringTransactionDao.getBySyncId(state.recurringTransactionSyncId)?.id ?: return
        val transactionId = state.transactionSyncId?.let { transactionDao.getBySyncId(it)?.id }

        occurrenceDao.upsert(
            RecurringTransactionOccurrenceEntity(
                id = local?.id ?: 0L,
                userId = userId,
                recurringTransactionId = recurringTransactionId,
                scheduledDate = state.scheduledDate,
                status = runCatching { OccurrenceStatus.valueOf(state.status) }.getOrDefault(local?.status ?: OccurrenceStatus.PENDING),
                transactionId = transactionId,
                processedAt = state.processedAt,
                createdAt = state.createdAt,
                syncId = state.id,
                updatedAt = state.updatedAt,
                deletedAt = state.deletedAt,
                version = state.version
            )
        )
    }

    /**
     * Voir [enqueueUnsyncedRecurringTransactions] pour le principe général — `recurringTransactionId`
     * n'est jamais nul (ligne ignorée silencieusement si sa règle parente n'a pas encore de `syncId`
     * résolvable, `?: return@forEach`), `transactionId` PEUT l'être (voir
     * [applyRecurringTransactionOccurrenceServerState]).
     */
    private suspend fun enqueueUnsyncedRecurringTransactionOccurrences(userId: Long) {
        occurrenceDao.getUnsyncedForUser(userId).forEach { occurrence ->
            val entity = occurrence.copy(syncId = UUID.randomUUID().toString())
            occurrenceDao.upsert(entity)

            val rule = recurringTransactionDao.getById(entity.recurringTransactionId, userId) ?: return@forEach
            val ruleSyncId = rule.syncId ?: UUID.randomUUID().toString().also { newSyncId ->
                recurringTransactionDao.upsert(rule.copy(syncId = newSyncId))
            }
            val transactionSyncId = entity.transactionId?.let { transactionId ->
                val transaction = transactionDao.getById(transactionId, userId) ?: return@let null
                transaction.syncId ?: UUID.randomUUID().toString().also { newSyncId ->
                    transactionDao.upsert(transaction.copy(syncId = newSyncId))
                }
            }

            val payload = RecurringTransactionOccurrenceSyncPayload(
                id = requireNotNull(entity.syncId),
                baseVersion = null,
                recurringTransactionSyncId = ruleSyncId,
                scheduledDate = entity.scheduledDate,
                status = entity.status.name,
                transactionSyncId = transactionSyncId,
                processedAt = entity.processedAt,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
            syncQueueEnqueuer.enqueue(
                entityType = "recurring_transaction_occurrences",
                entitySyncId = payload.id,
                operation = SyncOperation.CREATE,
                payloadJson = json.encodeToString(RecurringTransactionOccurrenceSyncPayload.serializer(), payload)
            )
        }
    }

    /**
     * DIFFÉRENT de [applyBudgetServerState]/[applyRecurringTransactionServerState] : `FinancialPlanItem`
     * référence TROIS autres entités synchronisées (planification parente, jamais nulle ; catégorie
     * ET transaction de conversion, toutes deux nullables) — voir la KDoc de tête de
     * `FinancialPlanItemSyncPayload.kt`.
     *
     * [state.planSyncId] introuvable localement : ligne ignorée silencieusement (`?: return`), comme
     * n'importe quelle entrée malformée (voir la KDoc de [pullEntityType]) — ne devrait quasiment
     * jamais arriver grâce à l'ordre de [SUPPORTED_ENTITY_TYPES] (`financial_plan_items` toujours
     * APRÈS `financial_plans`/`categories`/`transactions`).
     */
    private suspend fun applyFinancialPlanItemServerState(state: FinancialPlanItemServerStateDto, allowCreate: Boolean) {
        val local = financialPlanItemDao.getBySyncId(state.id)
        if (local == null && !allowCreate) return
        val userId = local?.userId ?: sessionManager.getCurrentUserIdOnce() ?: return
        val planId = financialPlanDao.getBySyncId(state.planSyncId)?.id ?: return
        val categoryId = state.categorySyncId?.let { categoryDao.getBySyncId(it)?.id }
        val transactionId = state.transactionSyncId?.let { transactionDao.getBySyncId(it)?.id }

        financialPlanItemDao.upsert(
            FinancialPlanItemEntity(
                id = local?.id ?: 0L,
                userId = userId,
                planId = planId,
                name = state.name,
                amount = state.amount,
                actualAmount = state.actualAmount,
                categoryId = categoryId,
                description = state.description,
                plannedDate = state.plannedDate,
                priority = runCatching { PlanItemPriority.valueOf(state.priority) }.getOrDefault(local?.priority ?: PlanItemPriority.IMPORTANT),
                status = runCatching { PlanItemStatus.valueOf(state.status) }.getOrDefault(local?.status ?: PlanItemStatus.TO_PLAN),
                transactionId = transactionId,
                createdAt = state.createdAt,
                updatedAt = state.updatedAt,
                syncId = state.id,
                deletedAt = state.deletedAt,
                version = state.version
            )
        )
    }

    /**
     * Voir [enqueueUnsyncedRecurringTransactions] pour le principe général — même raisonnement pour
     * la résolution `planId`/`categoryId`/`transactionId` → `planSyncId`/`categorySyncId`/
     * `transactionSyncId` (filet de sécurité inclus), DUPLIQUÉE ici plutôt que déléguée à une classe
     * partagée (un seul appelant : voir la KDoc de tête de `FinancialPlanItemSyncPayload.kt`).
     * `planId` (jamais nul) : ligne ignorée silencieusement (`?: return@forEach`) si la planification
     * parente est introuvable — jamais pour `categoryId`/`transactionId`, tous deux optionnels.
     */
    private suspend fun enqueueUnsyncedFinancialPlanItems(userId: Long) {
        financialPlanItemDao.getUnsyncedForUser(userId).forEach { item ->
            val entity = item.copy(syncId = UUID.randomUUID().toString())
            financialPlanItemDao.upsert(entity)

            val plan = financialPlanDao.getById(entity.planId, userId) ?: return@forEach
            val planSyncId = plan.syncId ?: UUID.randomUUID().toString().also { newSyncId ->
                financialPlanDao.upsert(plan.copy(syncId = newSyncId))
            }
            val categorySyncId = entity.categoryId?.let { categoryId ->
                val category = categoryDao.getById(categoryId, userId) ?: return@let null
                category.syncId ?: UUID.randomUUID().toString().also { newSyncId ->
                    categoryDao.upsert(category.copy(syncId = newSyncId))
                }
            }
            val transactionSyncId = entity.transactionId?.let { transactionId ->
                val transaction = transactionDao.getById(transactionId, userId) ?: return@let null
                transaction.syncId ?: UUID.randomUUID().toString().also { newSyncId ->
                    transactionDao.upsert(transaction.copy(syncId = newSyncId))
                }
            }

            val payload = FinancialPlanItemSyncPayload(
                id = requireNotNull(entity.syncId),
                baseVersion = null,
                planSyncId = planSyncId,
                name = entity.name,
                amount = entity.amount,
                actualAmount = entity.actualAmount,
                categorySyncId = categorySyncId,
                description = entity.description,
                plannedDate = entity.plannedDate,
                priority = entity.priority.name,
                status = entity.status.name,
                transactionSyncId = transactionSyncId,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
            syncQueueEnqueuer.enqueue(
                entityType = "financial_plan_items",
                entitySyncId = payload.id,
                operation = SyncOperation.CREATE,
                payloadJson = json.encodeToString(FinancialPlanItemSyncPayload.serializer(), payload)
            )
        }
    }

    /**
     * SEULE entité de tout ce moteur SANS AUCUNE référence croisée à résoudre (voir la KDoc de tête
     * de `UserPreferencesSyncPayload.kt`) — aucune contrainte d'ordre avec les autres entités dans
     * [SUPPORTED_ENTITY_TYPES]. `state.id` (`syncId`) résout directement la ligne locale, comme
     * [applyCategoryServerState] mais sans même un `categoryId`/`accountId` à convertir.
     */
    private suspend fun applyUserPreferencesServerState(state: UserPreferencesServerStateDto, allowCreate: Boolean) {
        val local = userPreferencesDao.getBySyncId(state.id)
        if (local == null && !allowCreate) return
        val userId = local?.userId ?: sessionManager.getCurrentUserIdOnce() ?: return

        userPreferencesDao.upsert(
            UserPreferencesEntity(
                id = local?.id ?: 0L,
                userId = userId,
                themeMode = runCatching { ThemeMode.valueOf(state.themeMode) }.getOrDefault(local?.themeMode ?: ThemeMode.SYSTEM),
                currencyCode = state.currencyCode,
                createdAt = state.createdAt,
                syncId = state.id,
                updatedAt = state.updatedAt,
                deletedAt = state.deletedAt,
                version = state.version
            )
        )
    }

    /** Voir [enqueueUnsyncedCategories] pour le principe général — PLUS SIMPLE ici : aucune
     *  référence croisée à résoudre (voir la KDoc de tête de `UserPreferencesSyncPayload.kt`), le
     *  payload se construit directement depuis [entity]. */
    private suspend fun enqueueUnsyncedUserPreferences(userId: Long) {
        userPreferencesDao.getUnsyncedForUser(userId).forEach { preferences ->
            val entity = preferences.copy(syncId = UUID.randomUUID().toString())
            userPreferencesDao.upsert(entity)

            val payload = UserPreferencesSyncPayload(
                id = requireNotNull(entity.syncId),
                baseVersion = null,
                themeMode = entity.themeMode.name,
                currencyCode = entity.currencyCode,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
            syncQueueEnqueuer.enqueue(
                entityType = "user_preferences",
                entitySyncId = payload.id,
                operation = SyncOperation.CREATE,
                payloadJson = json.encodeToString(UserPreferencesSyncPayload.serializer(), payload)
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
        /** `user_preferences` (dernier élément) : AUCUNE contrainte d'ordre, seule entité de ce
         *  registre sans référence croisée — voir [applyUserPreferencesServerState]. Sa place ici
         *  est arbitraire, contrairement à TOUTES les contraintes suivantes.
         *
         *  `financial_plan_items` DOIT rester juste avant lui mais APRÈS tout le reste (référence sa
         *  planification parente, sa catégorie ET sa transaction de conversion — les trois déjà
         *  présentes), `recurring_transaction_occurrences` juste avant lui (référence sa règle
         *  parente ET, une fois traitée, sa propre transaction), `recurring_transactions` juste
         *  avant (après `accounts`/`categories`), `loan_payments` juste avant
         *  `recurring_transactions`, `loans` juste avant lui (après `transactions`), `budgets` DOIT
         *  rester APRÈS `categories` — voir la KDoc de [enqueueUnsyncedLocalData]/
         *  [applyTransactionServerState]/[applyBudgetServerState]/[applyLoanServerState]/
         *  [applyLoanPaymentServerState]/[applyRecurringTransactionServerState]/
         *  [applyRecurringTransactionOccurrenceServerState]/[applyFinancialPlanItemServerState] :
         *  ces entités référencent d'autres entités synchronisées par leur `syncId`, qui doivent
         *  déjà être connues (pull) ou déjà persistées (push/backfill) au moment où elles sont
         *  traitées à leur tour. `setOf` (donc `LinkedHashSet`) préserve l'ordre d'insertion —
         *  [pullRemoteChanges] itère dans CET ordre. */
        val SUPPORTED_ENTITY_TYPES = setOf(
            "categories", "budgets", "savings_goals", "financial_plans", "persons", "accounts",
            "transactions", "loans", "loan_payments", "recurring_transactions",
            "recurring_transaction_occurrences", "financial_plan_items", "user_preferences"
        )
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
