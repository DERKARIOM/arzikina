package com.arzikina.ne.data.repository

import com.arzikina.ne.data.local.dao.AccountDao
import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.local.dao.TransactionDao
import com.arzikina.ne.data.local.entity.TransactionEntity
import com.arzikina.ne.data.remote.dto.TransactionSyncPayload
import com.arzikina.ne.domain.model.SyncOperation
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Point d'entrée UNIQUE pour enfiler la synchronisation d'une [TransactionEntity] — voir
 * `docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md`, étape 17. DIFFÉRENT des `enqueueXSync` privés des
 * autres entités (`CategoryRepositoryImpl.enqueueCategorySync`, etc.) : `Transaction` est écrite
 * depuis CINQ repositories (`TransactionRepositoryImpl`, `LoanRepositoryImpl`,
 * `RecurringTransactionRepositoryImpl`, `FinancialPlanRepositoryImpl`, plus les suppressions en
 * cascade de `AccountRepositoryImpl`/`PersonRepositoryImpl`) — dupliquer cette logique 5 fois
 * violerait "évite absolument le code dupliqué" (cahier des charges). Chacun de ces repositories
 * injecte cette classe au lieu de porter sa propre fonction d'enfilage.
 *
 * RÉSOLUTION `id` LOCAL → `syncId` : `Transaction` référence d'autres entités synchronisées
 * (compte, catégorie, transfert, transaction de frais) via des `id` Room locaux, qui n'ont aucun
 * sens d'un appareil à l'autre — voir la KDoc de tête de `TransactionSyncPayload.kt`. Ce moteur
 * résout donc [TransactionEntity.accountId]/[transferAccountId]/[categoryId]/[feeTransactionId] en
 * `syncId` AVANT de construire le payload. Filet de sécurité (should-never-happen, voir
 * [resolveOrAssignSyncId]) : si la ligne référencée n'a étonnamment pas encore de `syncId` (ne
 * devrait jamais arriver — un compte/catégorie reçoit son `syncId` dès SON PROPRE enregistrement,
 * pas seulement au push, voir `AccountRepositoryImpl.saveAccount`), un nouveau `syncId` est généré
 * et persisté à la volée, même réflexe défensif que `PersonRepositoryImpl.deletePerson`
 * (`existing.syncId ?: UUID.randomUUID()...`).
 *
 * ORDRE CRITIQUE (voir `SyncEngineImpl.enqueueUnsyncedLocalData`/`SUPPORTED_ENTITY_TYPES`) :
 * `transactions` doit toujours être traité APRÈS `accounts`/`categories`, aussi bien au backfill
 * (login) qu'au pull incrémental — sans quoi une transaction pourrait référencer un compte pas
 * encore connu localement. Voir leur KDoc respective pour le détail.
 */
@Singleton
class TransactionSyncEnqueuer @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val syncQueueEnqueuer: SyncQueueEnqueuer,
    private val json: Json
) {
    suspend fun enqueue(entity: TransactionEntity, operation: SyncOperation) {
        val userId = entity.userId
        val payload = TransactionSyncPayload(
            id = requireNotNull(entity.syncId) { "syncId doit être généré avant l'enfilage." },
            baseVersion = if (operation == SyncOperation.CREATE) null else entity.version,
            amount = entity.amount,
            type = entity.type.name,
            accountSyncId = resolveAccountSyncId(entity.accountId, userId),
            transferAccountSyncId = entity.transferAccountId?.let { resolveAccountSyncId(it, userId) },
            categorySyncId = entity.categoryId?.let { resolveCategorySyncId(it, userId) },
            date = entity.date,
            description = entity.description,
            latitude = entity.latitude,
            longitude = entity.longitude,
            paymentMethod = entity.paymentMethod?.name,
            feeTransactionSyncId = entity.feeTransactionId?.let { resolveTransactionSyncId(it, userId) },
            feeType = entity.feeType?.name,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
        syncQueueEnqueuer.enqueue(
            entityType = "transactions",
            entitySyncId = payload.id,
            operation = operation,
            payloadJson = json.encodeToString(TransactionSyncPayload.serializer(), payload)
        )
    }

    /** La ligne elle-même DOIT exister (contrainte `ForeignKey.CASCADE` réelle sur `accountId` —
     *  voir `TransactionEntity` — une transaction ne peut pas survivre à son compte) : son absence
     *  serait une corruption de données, pas un cas à absorber silencieusement, d'où `error()`. */
    private suspend fun resolveAccountSyncId(accountId: Long, userId: Long): String {
        val account = accountDao.getById(accountId, userId)
            ?: error("Compte introuvable pour la transaction (accountId=$accountId).")
        return resolveOrAssignSyncId(account.syncId) { newSyncId -> accountDao.upsert(account.copy(syncId = newSyncId)) }
    }

    private suspend fun resolveCategorySyncId(categoryId: Long, userId: Long): String {
        val category = categoryDao.getById(categoryId, userId)
            ?: error("Catégorie introuvable pour la transaction (categoryId=$categoryId).")
        return resolveOrAssignSyncId(category.syncId) { newSyncId -> categoryDao.upsert(category.copy(syncId = newSyncId)) }
    }

    /** Référence à une AUTRE transaction (la ligne de frais, voir `TransactionEntity.feeTransactionId`). */
    private suspend fun resolveTransactionSyncId(transactionId: Long, userId: Long): String {
        val transaction = transactionDao.getById(transactionId, userId)
            ?: error("Transaction de frais introuvable (id=$transactionId).")
        return resolveOrAssignSyncId(transaction.syncId) { newSyncId ->
            transactionDao.upsert(transaction.copy(syncId = newSyncId))
        }
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
