package com.arzikina.ne.data.repository

import androidx.room.withTransaction
import com.arzikina.ne.data.local.dao.AccountDao
import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.local.dao.RecurringTransactionDao
import com.arzikina.ne.data.local.dao.RecurringTransactionOccurrenceDao
import com.arzikina.ne.data.local.dao.TransactionDao
import com.arzikina.ne.data.local.database.ArzikinaDatabase
import com.arzikina.ne.data.local.entity.RecurringTransactionEntity
import com.arzikina.ne.data.local.entity.RecurringTransactionOccurrenceEntity
import com.arzikina.ne.data.local.entity.TransactionEntity
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.data.remote.dto.RecurringTransactionOccurrenceSyncPayload
import com.arzikina.ne.data.remote.dto.RecurringTransactionSyncPayload
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.OccurrenceStatus
import com.arzikina.ne.domain.model.PaymentMethod
import com.arzikina.ne.domain.model.RecurringTransaction
import com.arzikina.ne.domain.model.RecurringTransactionOccurrence
import com.arzikina.ne.domain.model.SyncOperation
import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.model.combineDayAndTime
import com.arzikina.ne.domain.model.computeNextExecutionDate
import com.arzikina.ne.domain.model.generateMissingScheduledDates
import com.arzikina.ne.domain.repository.AutomationScheduler
import com.arzikina.ne.domain.repository.RecurringTransactionRepository
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
 * Implémentation Room de [RecurringTransactionRepository].
 *
 * Isolation multi-utilisateurs : voir `AccountRepositoryImpl` pour le raisonnement.
 *
 * Écritures atomiques ([saveRecurringTransaction] pour une création, [deleteRecurringTransaction],
 * [acceptOccurrence], [acceptOccurrenceWithChanges], [rejectOccurrence], [generateMissingOccurrences])
 * via [ArzikinaDatabase.withTransaction] — même mécanisme que `LoanRepositoryImpl`.
 *
 * Dépend directement de [TransactionDao]/[AccountDao]/[CategoryDao] (pas de `TransactionRepository`/
 * `AccountRepository`/`CategoryRepository`) : voir la doc de `LoanRepositoryImpl` pour le même choix.
 * [TransactionSyncEnqueuer] injecté pour la même raison que `LoanRepositoryImpl` (voir sa KDoc de
 * tête). `RecurringTransactionEntity`/`RecurringTransactionOccurrenceEntity` sont des entités
 * synchronisées depuis l'étape 20 — [enqueueRecurringTransactionSync]/[enqueueOccurrenceSync] restent
 * PRIVÉES à cette classe (pas de classe partagée type `LoanSyncEnqueuer`) : un seul propriétaire
 * d'écriture pour ces deux entités, contrairement à `Transaction`/`Loan`.
 *
 * Toute mutation de `recurring_transactions`, y compris le recalcul de `nextExecutionDate`/
 * `isActive` fait par [generateMissingOccurrences] (fonction déjà présente AVANT la synchronisation,
 * mais dont l'effet restait purement local), doit désormais être enfilée — même raisonnement que
 * `LoanRepositoryImpl.recordPayment` pour le recalcul d'un prêt.
 *
 * Travaille avec les entités Room directement dans ses méthodes d'écriture (jamais de conversion
 * `toDomain()`/`toEntity()` intermédiaire inutile) : seules les méthodes de LECTURE PUBLIQUE
 * ([observeRecurringTransactions], [getRecurringTransaction], [observePendingOccurrences],
 * [observeProcessedOccurrences]) exposent des modèles domaine.
 *
 * [AutomationScheduler] tenu à jour depuis [saveRecurringTransaction]/[deleteRecurringTransaction]
 * (voir leur doc) — jamais depuis un Fragment/ViewModel (cahier des charges "Ajouter l'heure de
 * déclenchement à Automatisation", section 15) : ce repository reste le SEUL point d'entrée qui
 * modifie une règle, c'est donc aussi le seul endroit correct pour répercuter ce changement sur sa
 * programmation système. [generateMissingOccurrences] n'a pas besoin d'un appel équivalent : c'est
 * `AutomationAlarmReceiver` qui reprogramme après l'avoir appelée (voir sa doc), le Worker périodique
 * `RecurringOccurrencesWorker` restant volontairement silencieux de son côté (sert uniquement de
 * filet de sécurité, voir sa doc).
 */
class RecurringTransactionRepositoryImpl @Inject constructor(
    private val database: ArzikinaDatabase,
    private val recurringTransactionDao: RecurringTransactionDao,
    private val occurrenceDao: RecurringTransactionOccurrenceDao,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val sessionManager: SessionManager,
    private val automationScheduler: AutomationScheduler,
    private val transactionSyncEnqueuer: TransactionSyncEnqueuer,
    private val syncQueueEnqueuer: SyncQueueEnqueuer,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : RecurringTransactionRepository {

    override fun observeRecurringTransactions(): Flow<List<RecurringTransaction>> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                recurringTransactionDao.observeAllForUser(userId).map { entities -> entities.map { it.toDomain() } }
            }
        }

    override suspend fun getRecurringTransaction(id: Long): RecurringTransaction? =
        withContext(ioDispatcher) { recurringTransactionDao.getById(id, requireCurrentUserId())?.toDomain() }

    override fun observePendingOccurrences(): Flow<List<RecurringTransactionOccurrence>> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                occurrenceDao.observePendingForUser(userId).map { entities -> entities.map { it.toDomain() } }
            }
        }

    override fun observeProcessedOccurrences(): Flow<List<RecurringTransactionOccurrence>> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                occurrenceDao.observeProcessedForUser(userId).map { entities -> entities.map { it.toDomain() } }
            }
        }

    override suspend fun saveRecurringTransaction(recurringTransaction: RecurringTransaction): Long = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        var pendingRuleOp: Pair<RecurringTransactionEntity, SyncOperation>? = null

        val id = database.withTransaction {
            val now = System.currentTimeMillis()
            if (recurringTransaction.id == 0L) {
                val entity = recurringTransaction.copy(
                    nextExecutionDate = recurringTransaction.startDate,
                    isActive = true,
                    createdAt = now,
                    updatedAt = now
                ).toEntity(userId).copy(syncId = UUID.randomUUID().toString())
                val generatedId = recurringTransactionDao.upsert(entity)
                pendingRuleOp = entity.copy(id = generatedId) to SyncOperation.CREATE
                generatedId
            } else {
                val existing = recurringTransactionDao.getById(recurringTransaction.id, userId)
                    ?: error("Transaction récurrente introuvable.")
                // Ne rembobine jamais une progression déjà entamée (voir la doc de
                // `RecurringTransactionRepository.saveRecurringTransaction`) : seule une règle qui
                // n'a encore généré AUCUNE occurrence peut voir sa première échéance déplacée par un
                // changement de `startDate`. Détecté via l'historique d'occurrences plutôt que via
                // `nextExecutionDate == startDate` : ce dernier test échouerait à tort pour une
                // règle `RecurringFrequency.ONCE` déjà traitée (sa seule occurrence générée laisse
                // `nextExecutionDate` inchangée, voir `computeNextExecutionDate`, qui retourne
                // `null` pour ONCE — voir `generateMissingOccurrences`).
                val hasGeneratedOccurrences = occurrenceDao.getAllForRecurringTransaction(recurringTransaction.id, userId).isNotEmpty()
                val nextExecutionDate = if (hasGeneratedOccurrences) {
                    existing.nextExecutionDate
                } else {
                    recurringTransaction.startDate
                }
                val entity = recurringTransaction.copy(
                    nextExecutionDate = nextExecutionDate,
                    createdAt = existing.createdAt,
                    updatedAt = now
                ).toEntity(userId).copy(
                    syncId = existing.syncId ?: UUID.randomUUID().toString(),
                    deletedAt = existing.deletedAt,
                    version = existing.version
                )
                recurringTransactionDao.upsert(entity)
                pendingRuleOp = entity to SyncOperation.UPDATE
                recurringTransaction.id
            }
        }
        // Reprogramme l'alarme avec l'état DÉFINITIF après écriture (id généré pour une création,
        // nextExecutionDate/triggerHour/triggerMinute à jour pour une modification — voir cahier des
        // charges "Ajouter l'heure de déclenchement à Automatisation", section 6 : modifier l'heure
        // doit annuler proprement l'ancien déclenchement et programmer le nouveau, jamais de doublon).
        // Volontairement HORS de la transaction Room ci-dessus : un échec de programmation d'alarme
        // ne doit jamais faire annuler une écriture déjà validée en base.
        recurringTransactionDao.getById(id, userId)?.let { automationScheduler.schedule(it.toDomain()) }
        pendingRuleOp?.let { (entity, operation) -> enqueueRecurringTransactionSync(entity, operation) }
        id
    }

    override suspend fun deleteRecurringTransaction(id: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val pendingTransactionOps = mutableListOf<Pair<TransactionEntity, SyncOperation>>()
        val pendingOccurrenceOps = mutableListOf<Pair<RecurringTransactionOccurrenceEntity, SyncOperation>>()
        val pendingRuleOps = mutableListOf<Pair<RecurringTransactionEntity, SyncOperation>>()
        val now = System.currentTimeMillis()

        database.withTransaction {
            val rule = recurringTransactionDao.getById(id, userId) ?: return@withTransaction
            occurrenceDao.getAllForRecurringTransaction(id, userId).forEach { occurrence ->
                occurrence.transactionId?.let { transactionId ->
                    val transaction = transactionDao.getById(transactionId, userId)
                    transactionDao.softDeleteById(transactionId, userId, now)
                    if (transaction != null) {
                        pendingTransactionOps += transaction.copy(
                            syncId = transaction.syncId ?: UUID.randomUUID().toString(),
                            deletedAt = now,
                            updatedAt = now
                        ) to SyncOperation.DELETE
                    }
                }
                occurrenceDao.softDeleteById(occurrence.id, userId, now)
                pendingOccurrenceOps += occurrence.copy(
                    syncId = occurrence.syncId ?: UUID.randomUUID().toString(),
                    deletedAt = now,
                    updatedAt = now
                ) to SyncOperation.DELETE
            }
            // Supprime aussi, en cascade SQLite, tout l'historique d'occurrences de cette règle SI
            // jamais une soft-suppression avait été oubliée ci-dessus (filet de sécurité, ne
            // devrait plus se déclencher : un vrai `DELETE` ne se produit jamais sur cette table
            // depuis l'étape 20, voir `RecurringTransactionDao.softDeleteById`).
            recurringTransactionDao.softDeleteById(id, userId, now)
            pendingRuleOps += rule.copy(
                syncId = rule.syncId ?: UUID.randomUUID().toString(),
                deletedAt = now,
                updatedAt = now
            ) to SyncOperation.DELETE
        }

        pendingTransactionOps.forEach { (entity, operation) -> transactionSyncEnqueuer.enqueue(entity, operation) }
        pendingOccurrenceOps.forEach { (entity, operation) -> enqueueOccurrenceSync(entity, operation) }
        pendingRuleOps.forEach { (entity, operation) -> enqueueRecurringTransactionSync(entity, operation) }
        // Toujours appelé, même si la règle n'existait déjà plus ci-dessus (voir la doc de
        // `AutomationScheduler.cancel` : ne lève jamais d'exception si aucune alarme n'était
        // programmée) — voir cahier des charges section 7 : une suppression doit annuler le
        // déclenchement programmé.
        automationScheduler.cancel(id)
    }

    override suspend fun acceptOccurrence(occurrenceId: Long): Long = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        var pendingTransactionOp: Pair<TransactionEntity, SyncOperation>? = null
        var pendingOccurrenceOp: Pair<RecurringTransactionOccurrenceEntity, SyncOperation>? = null

        val result = database.withTransaction {
            val occurrence = pendingOccurrenceOrThrow(occurrenceId, userId)
            val rule = recurringTransactionDao.getById(occurrence.recurringTransactionId, userId)
                ?: error("Transaction récurrente introuvable.")
            val now = System.currentTimeMillis()
            val transactionEntity = Transaction(
                amount = rule.amount,
                type = rule.type,
                accountId = rule.accountId,
                categoryId = rule.categoryId,
                // Corrige le bug "toutes les automatisations s'exécutent à 00:00" : `scheduledDate`
                // est un jour calendaire normalisé à minuit local (voir sa doc), jamais l'heure
                // configurée par l'utilisateur — celle-ci vit sur la RÈGLE (`triggerHour`/
                // `triggerMinute`), à recombiner ici explicitement. Même fonction que
                // `AutomationSchedulerImpl`/l'affichage "Prochaine exécution" (voir
                // `RecurringTransactionTriggerTime.kt`), jamais dupliquée.
                date = combineDayAndTime(occurrence.scheduledDate, rule.triggerHour, rule.triggerMinute),
                description = rule.description,
                paymentMethod = rule.paymentMethod,
                createdAt = now
            ).toEntity(userId).copy(syncId = UUID.randomUUID().toString(), updatedAt = now)
            val transactionId = transactionDao.upsert(transactionEntity)
            pendingTransactionOp = transactionEntity.copy(id = transactionId) to SyncOperation.CREATE
            val updatedOccurrence = occurrence.copy(
                status = OccurrenceStatus.ACCEPTED,
                transactionId = transactionId,
                processedAt = now,
                updatedAt = now,
                syncId = occurrence.syncId ?: UUID.randomUUID().toString()
            )
            occurrenceDao.upsert(updatedOccurrence)
            pendingOccurrenceOp = updatedOccurrence to SyncOperation.UPDATE
            transactionId
        }

        pendingTransactionOp?.let { (entity, operation) -> transactionSyncEnqueuer.enqueue(entity, operation) }
        pendingOccurrenceOp?.let { (entity, operation) -> enqueueOccurrenceSync(entity, operation) }
        result
    }

    override suspend fun acceptOccurrenceWithChanges(
        occurrenceId: Long,
        type: TransactionType,
        amount: Long,
        accountId: Long,
        categoryId: Long?,
        date: Long,
        description: String,
        paymentMethod: PaymentMethod?
    ): Long = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        var pendingTransactionOp: Pair<TransactionEntity, SyncOperation>? = null
        var pendingOccurrenceOp: Pair<RecurringTransactionOccurrenceEntity, SyncOperation>? = null

        val result = database.withTransaction {
            val occurrence = pendingOccurrenceOrThrow(occurrenceId, userId)
            val now = System.currentTimeMillis()
            val transactionEntity = Transaction(
                amount = amount,
                type = type,
                accountId = accountId,
                categoryId = categoryId,
                date = date,
                description = description,
                paymentMethod = paymentMethod,
                createdAt = now
            ).toEntity(userId).copy(syncId = UUID.randomUUID().toString(), updatedAt = now)
            val transactionId = transactionDao.upsert(transactionEntity)
            pendingTransactionOp = transactionEntity.copy(id = transactionId) to SyncOperation.CREATE
            val updatedOccurrence = occurrence.copy(
                status = OccurrenceStatus.MODIFIED,
                transactionId = transactionId,
                processedAt = now,
                updatedAt = now,
                syncId = occurrence.syncId ?: UUID.randomUUID().toString()
            )
            occurrenceDao.upsert(updatedOccurrence)
            pendingOccurrenceOp = updatedOccurrence to SyncOperation.UPDATE
            transactionId
        }

        pendingTransactionOp?.let { (entity, operation) -> transactionSyncEnqueuer.enqueue(entity, operation) }
        pendingOccurrenceOp?.let { (entity, operation) -> enqueueOccurrenceSync(entity, operation) }
        result
    }

    override suspend fun rejectOccurrence(occurrenceId: Long): Unit = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val pendingOccurrenceOps = mutableListOf<Pair<RecurringTransactionOccurrenceEntity, SyncOperation>>()

        database.withTransaction {
            val occurrence = pendingOccurrenceOrThrow(occurrenceId, userId)
            val now = System.currentTimeMillis()
            val updatedOccurrence = occurrence.copy(
                status = OccurrenceStatus.REJECTED,
                processedAt = now,
                updatedAt = now,
                syncId = occurrence.syncId ?: UUID.randomUUID().toString()
            )
            occurrenceDao.upsert(updatedOccurrence)
            pendingOccurrenceOps += updatedOccurrence to SyncOperation.UPDATE
        }

        pendingOccurrenceOps.forEach { (entity, operation) -> enqueueOccurrenceSync(entity, operation) }
    }

    /**
     * Voir la doc de `RecurringTransactionRepository.generateMissingOccurrences` : ne fait rien si
     * aucun utilisateur n'est connecté, contrairement à [requireCurrentUserId] utilisée partout
     * ailleurs dans cette classe.
     */
    override suspend fun generateMissingOccurrences() = withContext(ioDispatcher) {
        val userId = sessionManager.getCurrentUserIdOnce() ?: return@withContext
        val now = System.currentTimeMillis()
        val pendingOccurrenceOps = mutableListOf<Pair<RecurringTransactionOccurrenceEntity, SyncOperation>>()
        val pendingRuleOps = mutableListOf<Pair<RecurringTransactionEntity, SyncOperation>>()

        database.withTransaction {
            recurringTransactionDao.getAllActiveForUser(userId).forEach { rule ->
                // `rule.triggerHour`/`rule.triggerMinute` passés EXPLICITEMENT ici (voir la doc de
                // `generateMissingScheduledDates`) : c'est le SEUL appel qui détermine "qu'est-ce qui
                // est dû MAINTENANT" — corrige le bug où le dialogue de validation s'affichait dès
                // l'ouverture de l'app le jour même, avant l'heure configurée par l'utilisateur.
                val dates = generateMissingScheduledDates(
                    rule.nextExecutionDate,
                    rule.frequency,
                    rule.endDate,
                    now,
                    rule.triggerHour,
                    rule.triggerMinute
                )
                if (dates.isEmpty()) {
                    deactivateIfPastEndDate(rule, now, pendingRuleOps)
                    return@forEach
                }

                dates.forEach { date ->
                    // Garde-fou : l'index unique `(recurringTransactionId, scheduledDate)` protège
                    // déjà la base, cette vérification évite en plus une exception à ce niveau si
                    // cette fonction est un jour appelée deux fois en parallèle (app + Worker).
                    if (!occurrenceDao.existsForDate(rule.id, date)) {
                        val occurrenceEntity = RecurringTransactionOccurrenceEntity(
                            userId = userId,
                            recurringTransactionId = rule.id,
                            scheduledDate = date,
                            status = OccurrenceStatus.PENDING,
                            transactionId = null,
                            processedAt = null,
                            createdAt = now,
                            syncId = UUID.randomUUID().toString(),
                            updatedAt = now
                        )
                        val occurrenceId = occurrenceDao.upsert(occurrenceEntity)
                        pendingOccurrenceOps += occurrenceEntity.copy(id = occurrenceId) to SyncOperation.CREATE
                    }
                }

                val lastGeneratedDate = dates.last()
                val nextCandidate = computeNextExecutionDate(lastGeneratedDate, rule.frequency)
                // Réutilise `generateMissingScheduledDates` (plutôt que de dupliquer sa comparaison
                // de jours calendaires) pour savoir si `nextCandidate` reste dans les bornes de la
                // règle (`endDate`) : appelée avec elle-même comme "aujourd'hui", elle renvoie soit
                // `[nextCandidate]` (encore valide), soit une liste vide (endDate dépassée).
                val hasMoreOccurrences = nextCandidate != null &&
                    generateMissingScheduledDates(nextCandidate, rule.frequency, rule.endDate, nextCandidate).isNotEmpty()

                val updatedRule = rule.copy(
                    nextExecutionDate = nextCandidate ?: rule.nextExecutionDate,
                    isActive = hasMoreOccurrences,
                    updatedAt = now,
                    syncId = rule.syncId ?: UUID.randomUUID().toString()
                )
                recurringTransactionDao.upsert(updatedRule)
                pendingRuleOps += updatedRule to SyncOperation.UPDATE
            }
        }

        pendingOccurrenceOps.forEach { (entity, operation) -> enqueueOccurrenceSync(entity, operation) }
        pendingRuleOps.forEach { (entity, operation) -> enqueueRecurringTransactionSync(entity, operation) }
    }

    /**
     * Garde-fou appelé quand [generateMissingScheduledDates] ne renvoie RIEN pour [rule] : deux
     * raisons possibles, à distinguer.
     * 1. `nextExecutionDate` n'est simplement pas encore due (cas normal, le plus fréquent) — ne
     *    rien faire, la règle reste active.
     * 2. `nextExecutionDate` est déjà postérieure à `endDate` alors que [rule] est encore
     *    `isActive` — ne peut normalement pas arriver via le déroulement habituel de cette fonction
     *    (qui désactive la règle exactement au moment où elle génère sa dernière occurrence valide,
     *    voir plus haut), mais PEUT arriver si `endDate` a été réduite après coup via
     *    `saveRecurringTransaction`, en dessous de `nextExecutionDate` déjà avancée : aucune
     *    validation du formulaire ne l'empêche actuellement (`RecurringTransactionFormViewModel.save`
     *    ne compare `endDate` qu'à `startDate`, jamais à `nextExecutionDate`, qu'il ne connaît même
     *    pas). Sans ce garde-fou, une telle règle resterait `isActive=true` indéfiniment sans jamais
     *    plus rien générer — et apparaîtrait à tort, pour toujours, dans la section "À venir" de
     *    `RecurringTransactionsViewModel` avec une échéance qui ne s'exécutera jamais.
     *
     * Réutilise [generateMissingScheduledDates] appelée avec `nextExecutionDate` comme sa propre
     * référence "aujourd'hui" (même principe que le calcul de `hasMoreOccurrences` plus haut) plutôt
     * que de dupliquer une comparaison de jours calendaires : renvoie une liste vide UNIQUEMENT si
     * `nextExecutionDate` est déjà postérieure à `endDate`, jamais pour une échéance simplement pas
     * encore due (elle reste alors <= à elle-même).
     *
     * [pendingRuleOps] : accumule la mutation pour enfilage — voir la doc de tête de la classe sur
     * le recalcul de règle désormais synchronisé.
     */
    private suspend fun deactivateIfPastEndDate(
        rule: RecurringTransactionEntity,
        now: Long,
        pendingRuleOps: MutableList<Pair<RecurringTransactionEntity, SyncOperation>>
    ) {
        val endDate = rule.endDate ?: return
        val isPastEndDate = generateMissingScheduledDates(rule.nextExecutionDate, rule.frequency, endDate, rule.nextExecutionDate).isEmpty()
        if (isPastEndDate) {
            val updatedRule = rule.copy(isActive = false, updatedAt = now, syncId = rule.syncId ?: UUID.randomUUID().toString())
            recurringTransactionDao.upsert(updatedRule)
            pendingRuleOps += updatedRule to SyncOperation.UPDATE
        }
    }

    private suspend fun pendingOccurrenceOrThrow(occurrenceId: Long, userId: Long) =
        occurrenceDao.getById(occurrenceId, userId)?.also {
            check(it.status == OccurrenceStatus.PENDING) { "Cette occurrence a déjà été traitée." }
        } ?: error("Occurrence introuvable.")

    private suspend fun enqueueRecurringTransactionSync(entity: RecurringTransactionEntity, operation: SyncOperation) {
        val payload = RecurringTransactionSyncPayload(
            id = requireNotNull(entity.syncId) { "syncId doit être généré avant l'enfilage." },
            baseVersion = if (operation == SyncOperation.CREATE) null else entity.version,
            type = entity.type.name,
            amount = entity.amount,
            accountSyncId = resolveAccountSyncId(entity.accountId, entity.userId),
            categorySyncId = entity.categoryId?.let { resolveCategorySyncId(it, entity.userId) },
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
            operation = operation,
            payloadJson = json.encodeToString(RecurringTransactionSyncPayload.serializer(), payload)
        )
    }

    private suspend fun enqueueOccurrenceSync(entity: RecurringTransactionOccurrenceEntity, operation: SyncOperation) {
        val payload = RecurringTransactionOccurrenceSyncPayload(
            id = requireNotNull(entity.syncId) { "syncId doit être généré avant l'enfilage." },
            baseVersion = if (operation == SyncOperation.CREATE) null else entity.version,
            recurringTransactionSyncId = resolveRecurringTransactionSyncId(entity.recurringTransactionId, entity.userId),
            scheduledDate = entity.scheduledDate,
            status = entity.status.name,
            transactionSyncId = entity.transactionId?.let { resolveTransactionSyncId(it, entity.userId) },
            processedAt = entity.processedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
        syncQueueEnqueuer.enqueue(
            entityType = "recurring_transaction_occurrences",
            entitySyncId = payload.id,
            operation = operation,
            payloadJson = json.encodeToString(RecurringTransactionOccurrenceSyncPayload.serializer(), payload)
        )
    }

    /** La ligne elle-même DOIT exister (contrainte `ForeignKey.CASCADE` réelle sur `accountId` —
     *  voir `RecurringTransactionEntity`) : son absence serait une corruption de données, pas un cas
     *  à absorber silencieusement, d'où `error()`. `getByIdIncludingDeleted` (pas `getById`) : voir
     *  `TransactionSyncEnqueuer.resolveAccountSyncId` pour le raisonnement complet (filet de
     *  sécurité contre une référence déjà soft-supprimée dans la même cascade). */
    private suspend fun resolveAccountSyncId(accountId: Long, userId: Long): String {
        val account = accountDao.getByIdIncludingDeleted(accountId, userId)
            ?: error("Compte introuvable pour la transaction récurrente (accountId=$accountId).")
        return resolveOrAssignSyncId(account.syncId) { newSyncId -> accountDao.upsert(account.copy(syncId = newSyncId)) }
    }

    private suspend fun resolveCategorySyncId(categoryId: Long, userId: Long): String {
        val category = categoryDao.getByIdIncludingDeleted(categoryId, userId)
            ?: error("Catégorie introuvable pour la transaction récurrente (categoryId=$categoryId).")
        return resolveOrAssignSyncId(category.syncId) { newSyncId -> categoryDao.upsert(category.copy(syncId = newSyncId)) }
    }

    /** Référence à la règle parente d'une occurrence. */
    private suspend fun resolveRecurringTransactionSyncId(recurringTransactionId: Long, userId: Long): String {
        val rule = recurringTransactionDao.getByIdIncludingDeleted(recurringTransactionId, userId)
            ?: error("Transaction récurrente introuvable pour l'occurrence (recurringTransactionId=$recurringTransactionId).")
        return resolveOrAssignSyncId(rule.syncId) { newSyncId -> recurringTransactionDao.upsert(rule.copy(syncId = newSyncId)) }
    }

    /** Référence à la transaction générée par une occurrence ACCEPTED/MODIFIED. */
    private suspend fun resolveTransactionSyncId(transactionId: Long, userId: Long): String {
        val transaction = transactionDao.getByIdIncludingDeleted(transactionId, userId)
            ?: error("Transaction introuvable pour l'occurrence (transactionId=$transactionId).")
        return resolveOrAssignSyncId(transaction.syncId) { newSyncId ->
            transactionDao.upsert(transaction.copy(syncId = newSyncId))
        }
    }

    /** Voir `TransactionSyncEnqueuer.resolveOrAssignSyncId` (même filet de sécurité, dupliqué ici
     *  volontairement : un seul appelant, une classe partagée n'apporterait rien). */
    private suspend fun resolveOrAssignSyncId(existing: String?, persist: suspend (String) -> Unit): String {
        existing?.let { return it }
        val newSyncId = UUID.randomUUID().toString()
        persist(newSyncId)
        return newSyncId
    }

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
