package com.naniger.arzikina.data.repository

import com.naniger.arzikina.data.local.dao.AccountDao
import com.naniger.arzikina.data.local.dao.LoanDao
import com.naniger.arzikina.data.local.dao.LoanPaymentDao
import com.naniger.arzikina.data.local.dao.PersonDao
import com.naniger.arzikina.data.local.dao.TransactionDao
import com.naniger.arzikina.data.local.entity.LoanEntity
import com.naniger.arzikina.data.local.entity.LoanPaymentEntity
import com.naniger.arzikina.data.remote.dto.LoanPaymentSyncPayload
import com.naniger.arzikina.data.remote.dto.LoanSyncPayload
import com.naniger.arzikina.domain.model.SyncOperation
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Point d'entrée UNIQUE pour enfiler la synchronisation d'un [LoanEntity]/[LoanPaymentEntity] —
 * voir `docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md`, étape 19. DIFFÉRENT des `enqueueXSync` privés de
 * `Budget`/`Category`/etc. (un seul propriétaire chacune) : `Loan`/`LoanPayment` sont écrites depuis
 * TROIS repositories (`LoanRepositoryImpl`, propriétaire normal, plus `AccountRepositoryImpl` et
 * `PersonRepositoryImpl`, qui les suppriment en cascade) — dupliquer cette logique 3 fois violerait
 * "évite absolument le code dupliqué" (cahier des charges), même raisonnement que
 * [TransactionSyncEnqueuer] (voir sa KDoc de tête).
 *
 * RÉSOLUTION `id` LOCAL → `syncId` : voir la KDoc de tête de [TransactionSyncEnqueuer] pour le
 * raisonnement complet (même filet de sécurité [resolveOrAssignSyncId] partout où une ligne
 * référencée n'a étonnamment pas encore de `syncId`).
 *
 * ORDRE CRITIQUE (voir `SyncEngineImpl.enqueueUnsyncedLocalData`/`SUPPORTED_ENTITY_TYPES`) :
 * `loans` doit être traité APRÈS `persons`/`accounts`/`transactions`, `loan_payments` APRÈS `loans`
 * (et `transactions`) — un prêt/emprunt référence sa transaction de décaissement, un remboursement
 * référence son prêt parent ET sa propre transaction.
 */
@Singleton
class LoanSyncEnqueuer @Inject constructor(
    private val loanDao: LoanDao,
    private val loanPaymentDao: LoanPaymentDao,
    private val personDao: PersonDao,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val syncQueueEnqueuer: SyncQueueEnqueuer,
    private val json: Json
) {
    suspend fun enqueueLoan(entity: LoanEntity, operation: SyncOperation) {
        val userId = entity.userId
        val payload = LoanSyncPayload(
            id = requireNotNull(entity.syncId) { "syncId doit être généré avant l'enfilage." },
            baseVersion = if (operation == SyncOperation.CREATE) null else entity.version,
            personSyncId = resolvePersonSyncId(entity.personId, userId),
            accountSyncId = resolveAccountSyncId(entity.accountId, userId),
            type = entity.type.name,
            amount = entity.amount,
            amountRepaid = entity.amountRepaid,
            remainingAmount = entity.remainingAmount,
            startDate = entity.startDate,
            dueDate = entity.dueDate,
            reason = entity.reason.name,
            reasonCustomText = entity.reasonCustomText,
            repaymentMode = entity.repaymentMode.name,
            description = entity.description,
            status = entity.status.name,
            transactionSyncId = resolveTransactionSyncId(entity.transactionId, userId),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
        syncQueueEnqueuer.enqueue(
            entityType = "loans",
            entitySyncId = payload.id,
            operation = operation,
            payloadJson = json.encodeToString(LoanSyncPayload.serializer(), payload)
        )
    }

    suspend fun enqueueLoanPayment(entity: LoanPaymentEntity, operation: SyncOperation) {
        val userId = entity.userId
        val payload = LoanPaymentSyncPayload(
            id = requireNotNull(entity.syncId) { "syncId doit être généré avant l'enfilage." },
            baseVersion = if (operation == SyncOperation.CREATE) null else entity.version,
            loanSyncId = resolveLoanSyncId(entity.loanId, userId),
            accountSyncId = resolveAccountSyncId(entity.accountId, userId),
            amount = entity.amount,
            date = entity.date,
            note = entity.note,
            transactionSyncId = resolveTransactionSyncId(entity.transactionId, userId),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
        syncQueueEnqueuer.enqueue(
            entityType = "loan_payments",
            entitySyncId = payload.id,
            operation = operation,
            payloadJson = json.encodeToString(LoanPaymentSyncPayload.serializer(), payload)
        )
    }

    /** La ligne elle-même DOIT exister (contrainte `ForeignKey.CASCADE` réelle sur `personId` —
     *  voir `LoanEntity` — un prêt/emprunt ne peut pas survivre à sa personne) : son absence serait
     *  une corruption de données, pas un cas à absorber silencieusement, d'où `error()`.
     *
     *  `getByIdIncludingDeleted` (PAS `getById`, voir `TransactionSyncEnqueuer.resolveAccountSyncId`
     *  pour le raisonnement complet — bug réel corrigé à l'étape 19.5) : cette personne peut avoir
     *  déjà été soft-supprimée dans la MÊME cascade que ce prêt/emprunt (voir
     *  `PersonRepositoryImpl.deletePerson`, où l'enfilage a lieu APRÈS le commit de
     *  `personDao.softDeleteById`) — son `syncId` reste valide et doit être résolu normalement. */
    private suspend fun resolvePersonSyncId(personId: Long, userId: Long): String {
        val person = personDao.getByIdIncludingDeleted(personId, userId)
            ?: error("Personne introuvable pour le prêt/emprunt (personId=$personId).")
        return resolveOrAssignSyncId(person.syncId) { newSyncId -> personDao.upsert(person.copy(syncId = newSyncId)) }
    }

    /** Voir la KDoc de [resolvePersonSyncId] (même raisonnement `getByIdIncludingDeleted` : ce
     *  compte peut avoir déjà été soft-supprimé dans la même cascade, voir
     *  `AccountRepositoryImpl.deleteAccount`). */
    private suspend fun resolveAccountSyncId(accountId: Long, userId: Long): String {
        val account = accountDao.getByIdIncludingDeleted(accountId, userId)
            ?: error("Compte introuvable pour le prêt/emprunt (accountId=$accountId).")
        return resolveOrAssignSyncId(account.syncId) { newSyncId -> accountDao.upsert(account.copy(syncId = newSyncId)) }
    }

    /** Voir la KDoc de [resolvePersonSyncId] (même raisonnement `getByIdIncludingDeleted`). */
    private suspend fun resolveTransactionSyncId(transactionId: Long, userId: Long): String {
        val transaction = transactionDao.getByIdIncludingDeleted(transactionId, userId)
            ?: error("Transaction introuvable pour le prêt/emprunt (transactionId=$transactionId).")
        return resolveOrAssignSyncId(transaction.syncId) { newSyncId ->
            transactionDao.upsert(transaction.copy(syncId = newSyncId))
        }
    }

    /** Voir la KDoc de [resolvePersonSyncId] (même raisonnement `getByIdIncludingDeleted` : ce prêt
     *  peut avoir déjà été soft-supprimé dans la même cascade que son remboursement, voir
     *  `LoanRepositoryImpl.deleteLoan`). */
    private suspend fun resolveLoanSyncId(loanId: Long, userId: Long): String {
        val loan = loanDao.getByIdIncludingDeleted(loanId, userId)
            ?: error("Prêt/emprunt introuvable pour le remboursement (loanId=$loanId).")
        return resolveOrAssignSyncId(loan.syncId) { newSyncId -> loanDao.upsert(loan.copy(syncId = newSyncId)) }
    }

    /** Voir la KDoc de tête (filet de sécurité) : génère et persiste un `syncId` si [existing] est
     *  `null`, ne devrait quasiment jamais s'activer en pratique. */
    private suspend fun resolveOrAssignSyncId(existing: String?, persist: suspend (String) -> Unit): String {
        existing?.let { return it }
        val newSyncId = UUID.randomUUID().toString()
        persist(newSyncId)
        return newSyncId
    }
}
