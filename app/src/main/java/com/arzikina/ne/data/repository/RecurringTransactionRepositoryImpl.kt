package com.arzikina.ne.data.repository

import androidx.room.withTransaction
import com.arzikina.ne.data.local.dao.RecurringTransactionDao
import com.arzikina.ne.data.local.dao.RecurringTransactionOccurrenceDao
import com.arzikina.ne.data.local.dao.TransactionDao
import com.arzikina.ne.data.local.database.ArzikinaDatabase
import com.arzikina.ne.data.local.entity.RecurringTransactionEntity
import com.arzikina.ne.data.local.entity.RecurringTransactionOccurrenceEntity
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.OccurrenceStatus
import com.arzikina.ne.domain.model.PaymentMethod
import com.arzikina.ne.domain.model.RecurringTransaction
import com.arzikina.ne.domain.model.RecurringTransactionOccurrence
import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.model.TransactionType
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
 * Dépend directement de [TransactionDao] (pas de `TransactionRepository`) : voir la doc de
 * `LoanRepositoryImpl` pour le même choix.
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
    private val sessionManager: SessionManager,
    private val automationScheduler: AutomationScheduler,
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
        val id = database.withTransaction {
            val now = System.currentTimeMillis()
            if (recurringTransaction.id == 0L) {
                recurringTransactionDao.upsert(
                    recurringTransaction.copy(
                        nextExecutionDate = recurringTransaction.startDate,
                        isActive = true,
                        createdAt = now,
                        updatedAt = now
                    ).toEntity(userId)
                )
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
                recurringTransactionDao.upsert(
                    recurringTransaction.copy(
                        nextExecutionDate = nextExecutionDate,
                        createdAt = existing.createdAt,
                        updatedAt = now
                    ).toEntity(userId)
                )
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
        id
    }

    override suspend fun deleteRecurringTransaction(id: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        database.withTransaction {
            recurringTransactionDao.getById(id, userId) ?: return@withTransaction
            occurrenceDao.getAllForRecurringTransaction(id, userId)
                .mapNotNull { it.transactionId }
                .forEach { transactionDao.deleteById(it, userId) }
            // Supprime aussi, en cascade SQLite, tout l'historique d'occurrences de cette règle.
            recurringTransactionDao.deleteById(id, userId)
        }
        // Toujours appelé, même si la règle n'existait déjà plus ci-dessus (voir la doc de
        // `AutomationScheduler.cancel` : ne lève jamais d'exception si aucune alarme n'était
        // programmée) — voir cahier des charges section 7 : une suppression doit annuler le
        // déclenchement programmé.
        automationScheduler.cancel(id)
    }

    override suspend fun acceptOccurrence(occurrenceId: Long): Long = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        database.withTransaction {
            val occurrence = pendingOccurrenceOrThrow(occurrenceId, userId)
            val rule = recurringTransactionDao.getById(occurrence.recurringTransactionId, userId)
                ?: error("Transaction récurrente introuvable.")
            val now = System.currentTimeMillis()
            val transactionId = transactionDao.upsert(
                Transaction(
                    amount = rule.amount,
                    type = rule.type,
                    accountId = rule.accountId,
                    categoryId = rule.categoryId,
                    date = occurrence.scheduledDate,
                    description = rule.description,
                    paymentMethod = rule.paymentMethod,
                    createdAt = now
                ).toEntity(userId)
            )
            occurrenceDao.upsert(
                occurrence.copy(status = OccurrenceStatus.ACCEPTED, transactionId = transactionId, processedAt = now)
            )
            transactionId
        }
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
        database.withTransaction {
            val occurrence = pendingOccurrenceOrThrow(occurrenceId, userId)
            val now = System.currentTimeMillis()
            val transactionId = transactionDao.upsert(
                Transaction(
                    amount = amount,
                    type = type,
                    accountId = accountId,
                    categoryId = categoryId,
                    date = date,
                    description = description,
                    paymentMethod = paymentMethod,
                    createdAt = now
                ).toEntity(userId)
            )
            occurrenceDao.upsert(
                occurrence.copy(status = OccurrenceStatus.MODIFIED, transactionId = transactionId, processedAt = now)
            )
            transactionId
        }
    }

    // Type de retour Unit explicite (contrairement aux autres méthodes de cette classe) : sans lui,
    // Kotlin infère le retour de `occurrenceDao.upsert(...)` (Long) comme type de la fonction, ce
    // qui ne correspond pas à la signature Unit de `RecurringTransactionRepository.rejectOccurrence`
    // — erreur de compilation constatée à la construction, corrigée ici.
    override suspend fun rejectOccurrence(occurrenceId: Long): Unit = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        database.withTransaction {
            val occurrence = pendingOccurrenceOrThrow(occurrenceId, userId)
            occurrenceDao.upsert(occurrence.copy(status = OccurrenceStatus.REJECTED, processedAt = System.currentTimeMillis()))
        }
    }

    /**
     * Voir la doc de `RecurringTransactionRepository.generateMissingOccurrences` : ne fait rien si
     * aucun utilisateur n'est connecté, contrairement à [requireCurrentUserId] utilisée partout
     * ailleurs dans cette classe.
     */
    override suspend fun generateMissingOccurrences() = withContext(ioDispatcher) {
        val userId = sessionManager.getCurrentUserIdOnce() ?: return@withContext
        val now = System.currentTimeMillis()
        database.withTransaction {
            recurringTransactionDao.getAllActiveForUser(userId).forEach { rule ->
                val dates = generateMissingScheduledDates(rule.nextExecutionDate, rule.frequency, rule.endDate, now)
                if (dates.isEmpty()) {
                    deactivateIfPastEndDate(rule, now)
                    return@forEach
                }

                dates.forEach { date ->
                    // Garde-fou : l'index unique `(recurringTransactionId, scheduledDate)` protège
                    // déjà la base, cette vérification évite en plus une exception à ce niveau si
                    // cette fonction est un jour appelée deux fois en parallèle (app + Worker).
                    if (!occurrenceDao.existsForDate(rule.id, date)) {
                        occurrenceDao.upsert(
                            RecurringTransactionOccurrenceEntity(
                                userId = userId,
                                recurringTransactionId = rule.id,
                                scheduledDate = date,
                                status = OccurrenceStatus.PENDING,
                                transactionId = null,
                                processedAt = null,
                                createdAt = now
                            )
                        )
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

                recurringTransactionDao.upsert(
                    rule.copy(
                        nextExecutionDate = nextCandidate ?: rule.nextExecutionDate,
                        isActive = hasMoreOccurrences,
                        updatedAt = now
                    )
                )
            }
        }
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
     */
    private suspend fun deactivateIfPastEndDate(rule: RecurringTransactionEntity, now: Long) {
        val endDate = rule.endDate ?: return
        val isPastEndDate = generateMissingScheduledDates(rule.nextExecutionDate, rule.frequency, endDate, rule.nextExecutionDate).isEmpty()
        if (isPastEndDate) {
            recurringTransactionDao.upsert(rule.copy(isActive = false, updatedAt = now))
        }
    }

    private suspend fun pendingOccurrenceOrThrow(occurrenceId: Long, userId: Long) =
        occurrenceDao.getById(occurrenceId, userId)?.also {
            check(it.status == OccurrenceStatus.PENDING) { "Cette occurrence a déjà été traitée." }
        } ?: error("Occurrence introuvable.")

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
