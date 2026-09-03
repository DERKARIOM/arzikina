package com.arzikina.ne.data.repository

import androidx.room.withTransaction
import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.local.dao.LoanDao
import com.arzikina.ne.data.local.dao.LoanPaymentDao
import com.arzikina.ne.data.local.dao.TransactionDao
import com.arzikina.ne.data.local.database.ArzikinaDatabase
import com.arzikina.ne.data.local.database.SystemCategoryResolver
import com.arzikina.ne.data.local.entity.CategoryEntity
import com.arzikina.ne.data.local.entity.LoanEntity
import com.arzikina.ne.data.local.entity.LoanPaymentEntity
import com.arzikina.ne.data.local.entity.TransactionEntity
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.Loan
import com.arzikina.ne.domain.model.LoanCategoryNames
import com.arzikina.ne.domain.model.LoanPayment
import com.arzikina.ne.domain.model.LoanType
import com.arzikina.ne.domain.model.SyncOperation
import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.model.computeLoanStatus
import com.arzikina.ne.domain.repository.LoanRepository
import com.arzikina.ne.domain.repository.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * Implémentation Room de [LoanRepository].
 *
 * Isolation multi-utilisateurs : voir `AccountRepositoryImpl` pour le raisonnement.
 *
 * Écritures atomiques ([saveLoan] pour une création, [recordPayment], [deleteLoan],
 * [deletePayment]) via [ArzikinaDatabase.withTransaction] (même mécanisme que
 * `BackupRepositoryImpl` pour sa restauration) : le prêt/emprunt et sa transaction Arzikina liée
 * sont créés/supprimés ensemble, ou pas du tout.
 *
 * Dépend directement de [TransactionDao]/[CategoryDao] (pas de `TransactionRepository`/
 * `CategoryRepository`) : un repository ne doit pas dépendre d'un autre repository pour rester
 * libre de composer plusieurs DAO dans une seule transaction Room — voir `BackupRepositoryImpl`,
 * qui suit le même principe. [TransactionSyncEnqueuer]/[LoanSyncEnqueuer] font exception (voir
 * leur KDoc de tête) : CE repository est l'un des CINQ qui écrivent des transactions ET l'un des
 * TROIS qui écrivent `Loan`/`LoanPayment`, mais reste seul PROPRIÉTAIRE normal de `LoanDao`/
 * `LoanPaymentDao` (jamais de `LoanRepository` externe).
 *
 * `Loan`/`LoanPayment` sont des entités synchronisées depuis l'étape 19 (voir
 * `docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md`) — toute mutation de ces tables, y compris le
 * recalcul de `amountRepaid`/`remainingAmount`/`status` d'un prêt/emprunt suite à un remboursement
 * (fonction déjà présente AVANT la synchronisation, mais dont l'effet restait purement local), doit
 * désormais être enfilée via [loanSyncEnqueuer] — sans quoi la progression d'un remboursement
 * n'apparaîtrait jamais sur un second appareil.
 */
class LoanRepositoryImpl @Inject constructor(
    private val database: ArzikinaDatabase,
    private val loanDao: LoanDao,
    private val loanPaymentDao: LoanPaymentDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val sessionManager: SessionManager,
    private val transactionSyncEnqueuer: TransactionSyncEnqueuer,
    private val loanSyncEnqueuer: LoanSyncEnqueuer,
    private val categorySyncEnqueuer: CategorySyncEnqueuer,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : LoanRepository {

    override fun observeLoans(): Flow<List<Loan>> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                loanDao.observeAllForUser(userId).map { entities -> entities.map { it.toDomain() } }
            }
        }

    override suspend fun getLoan(id: Long): Loan? =
        withContext(ioDispatcher) { loanDao.getById(id, requireCurrentUserId())?.toDomain() }

    override fun observePayments(loanId: Long): Flow<List<LoanPayment>> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                loanPaymentDao.observeForLoan(loanId, userId).map { entities -> entities.map { it.toDomain() } }
            }
        }

    override suspend fun saveLoan(loan: Loan): Long = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val pendingTransactionOps = mutableListOf<Pair<TransactionEntity, SyncOperation>>()
        val pendingLoanOps = mutableListOf<Pair<LoanEntity, SyncOperation>>()

        val savedId = database.withTransaction {
            if (loan.id == 0L) {
                val now = System.currentTimeMillis()
                val category = resolveLoanCategory(disbursementCategoryName(loan.type), userId)
                val transactionEntity = Transaction(
                    amount = loan.amount,
                    type = disbursementTransactionType(loan.type),
                    accountId = loan.accountId,
                    categoryId = category.id,
                    date = loan.startDate,
                    description = loan.description.ifBlank { category.name },
                    createdAt = now
                ).toEntity(userId).copy(syncId = UUID.randomUUID().toString(), updatedAt = now)
                val transactionId = transactionDao.upsert(transactionEntity)
                pendingTransactionOps += transactionEntity.copy(id = transactionId) to SyncOperation.CREATE

                val status = computeLoanStatus(loan.amount, 0L, loan.startDate, loan.dueDate, now)
                val loanEntity = loan.copy(
                    amountRepaid = 0L,
                    remainingAmount = loan.amount,
                    status = status,
                    transactionId = transactionId,
                    createdAt = now,
                    updatedAt = now
                ).toEntity(userId).copy(syncId = UUID.randomUUID().toString())
                val loanId = loanDao.upsert(loanEntity)
                pendingLoanOps += loanEntity.copy(id = loanId) to SyncOperation.CREATE
                loanId
            } else {
                val existing = loanDao.getById(loan.id, userId) ?: error("Prêt/emprunt introuvable.")
                val now = System.currentTimeMillis()
                val status = computeLoanStatus(loan.amount, existing.amountRepaid, loan.startDate, loan.dueDate, now)
                // La transaction de décaissement déjà créée (voir la doc de [Loan.transactionId])
                // DOIT rester synchronisée avec les champs modifiables ici (montant, compte, date,
                // description) — sans ceci, "Détail du compte"/"Transactions" continuerait
                // d'afficher l'ancien montant/compte alors que le prêt/emprunt affiche le nouveau.
                // Aucun écran n'atteint encore cette branche aujourd'hui (édition non prévue à ce
                // stade du plan, voir nav_graph.xml/loanFormFragment), mais la corriger maintenant
                // évite une régression silencieuse le jour où l'édition sera ajoutée.
                val existingTransaction = transactionDao.getById(existing.transactionId, userId)
                if (existingTransaction != null) {
                    val category = resolveLoanCategory(disbursementCategoryName(loan.type), userId)
                    val updatedTransaction = existingTransaction.copy(
                        amount = loan.amount,
                        accountId = loan.accountId,
                        categoryId = category.id,
                        date = loan.startDate,
                        description = loan.description.ifBlank { category.name },
                        // syncId/version PRÉSERVÉS (filet de sécurité si jamais absent — voir
                        // `TransactionRepositoryImpl.saveTransaction`) : ceci reste une MISE À JOUR
                        // EN PLACE, jamais une nouvelle ligne.
                        syncId = existingTransaction.syncId ?: UUID.randomUUID().toString(),
                        updatedAt = now
                    )
                    transactionDao.upsert(updatedTransaction)
                    pendingTransactionOps += updatedTransaction to SyncOperation.UPDATE
                }
                val loanEntity = loan.copy(
                    amountRepaid = existing.amountRepaid,
                    remainingAmount = loan.amount - existing.amountRepaid,
                    status = status,
                    transactionId = existing.transactionId,
                    updatedAt = now
                ).toEntity(userId).copy(
                    syncId = existing.syncId ?: UUID.randomUUID().toString(),
                    deletedAt = existing.deletedAt,
                    version = existing.version
                )
                loanDao.upsert(loanEntity)
                pendingLoanOps += loanEntity to SyncOperation.UPDATE
                loan.id
            }
        }

        pendingTransactionOps.forEach { (entity, operation) -> transactionSyncEnqueuer.enqueue(entity, operation) }
        pendingLoanOps.forEach { (entity, operation) -> loanSyncEnqueuer.enqueueLoan(entity, operation) }
        savedId
    }

    override suspend fun deleteLoan(id: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val pendingTransactionOps = mutableListOf<Pair<TransactionEntity, SyncOperation>>()
        val pendingLoanPaymentOps = mutableListOf<Pair<LoanPaymentEntity, SyncOperation>>()
        val pendingLoanOps = mutableListOf<Pair<LoanEntity, SyncOperation>>()
        val now = System.currentTimeMillis()

        database.withTransaction {
            val loan = loanDao.getById(id, userId) ?: return@withTransaction
            loanPaymentDao.getAllForLoan(id, userId).forEach { payment ->
                val paymentTransaction = transactionDao.getById(payment.transactionId, userId)
                transactionDao.softDeleteById(payment.transactionId, userId, now)
                if (paymentTransaction != null) {
                    pendingTransactionOps += paymentTransaction.copy(
                        syncId = paymentTransaction.syncId ?: UUID.randomUUID().toString(),
                        deletedAt = now,
                        updatedAt = now
                    ) to SyncOperation.DELETE
                }
                loanPaymentDao.softDeleteById(payment.id, userId, now)
                pendingLoanPaymentOps += payment.copy(
                    syncId = payment.syncId ?: UUID.randomUUID().toString(),
                    deletedAt = now,
                    updatedAt = now
                ) to SyncOperation.DELETE
            }
            val loanTransaction = transactionDao.getById(loan.transactionId, userId)
            transactionDao.softDeleteById(loan.transactionId, userId, now)
            if (loanTransaction != null) {
                pendingTransactionOps += loanTransaction.copy(
                    syncId = loanTransaction.syncId ?: UUID.randomUUID().toString(),
                    deletedAt = now,
                    updatedAt = now
                ) to SyncOperation.DELETE
            }
            loanDao.softDeleteById(id, userId, now)
            pendingLoanOps += loan.copy(
                syncId = loan.syncId ?: UUID.randomUUID().toString(),
                deletedAt = now,
                updatedAt = now
            ) to SyncOperation.DELETE
        }

        pendingTransactionOps.forEach { (entity, operation) -> transactionSyncEnqueuer.enqueue(entity, operation) }
        pendingLoanPaymentOps.forEach { (entity, operation) -> loanSyncEnqueuer.enqueueLoanPayment(entity, operation) }
        pendingLoanOps.forEach { (entity, operation) -> loanSyncEnqueuer.enqueueLoan(entity, operation) }
    }

    override suspend fun recordPayment(payment: LoanPayment): Long = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val pendingTransactionOps = mutableListOf<Pair<TransactionEntity, SyncOperation>>()
        val pendingLoanOps = mutableListOf<Pair<LoanEntity, SyncOperation>>()
        val pendingLoanPaymentOps = mutableListOf<Pair<LoanPaymentEntity, SyncOperation>>()

        val result = database.withTransaction {
            val loan = loanDao.getById(payment.loanId, userId) ?: error("Prêt/emprunt introuvable.")
            check(payment.amount in 1..loan.remainingAmount) {
                "Le montant du remboursement dépasse le solde restant du prêt/emprunt."
            }
            val now = System.currentTimeMillis()
            val category = resolveLoanCategory(repaymentCategoryName(loan.type), userId)
            val transactionEntity = Transaction(
                amount = payment.amount,
                type = repaymentTransactionType(loan.type),
                accountId = payment.accountId,
                categoryId = category.id,
                date = payment.date,
                description = payment.note.ifBlank { category.name },
                createdAt = now
            ).toEntity(userId).copy(syncId = UUID.randomUUID().toString(), updatedAt = now)
            val transactionId = transactionDao.upsert(transactionEntity)
            pendingTransactionOps += transactionEntity.copy(id = transactionId) to SyncOperation.CREATE

            val newAmountRepaid = loan.amountRepaid + payment.amount
            val newStatus = computeLoanStatus(loan.amount, newAmountRepaid, loan.startDate, loan.dueDate, now)
            val updatedLoan = loan.copy(
                amountRepaid = newAmountRepaid,
                remainingAmount = loan.amount - newAmountRepaid,
                status = newStatus,
                updatedAt = now,
                syncId = loan.syncId ?: UUID.randomUUID().toString()
            )
            loanDao.upsert(updatedLoan)
            pendingLoanOps += updatedLoan to SyncOperation.UPDATE

            val paymentEntity = payment.copy(transactionId = transactionId, createdAt = now)
                .toEntity(userId).copy(syncId = UUID.randomUUID().toString(), updatedAt = now)
            val paymentId = loanPaymentDao.upsert(paymentEntity)
            pendingLoanPaymentOps += paymentEntity.copy(id = paymentId) to SyncOperation.CREATE
            paymentId
        }

        pendingTransactionOps.forEach { (entity, operation) -> transactionSyncEnqueuer.enqueue(entity, operation) }
        pendingLoanOps.forEach { (entity, operation) -> loanSyncEnqueuer.enqueueLoan(entity, operation) }
        pendingLoanPaymentOps.forEach { (entity, operation) -> loanSyncEnqueuer.enqueueLoanPayment(entity, operation) }
        result
    }

    override suspend fun deletePayment(id: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val pendingTransactionOps = mutableListOf<Pair<TransactionEntity, SyncOperation>>()
        val pendingLoanOps = mutableListOf<Pair<LoanEntity, SyncOperation>>()
        val pendingLoanPaymentOps = mutableListOf<Pair<LoanPaymentEntity, SyncOperation>>()
        val now = System.currentTimeMillis()

        database.withTransaction {
            val payment = loanPaymentDao.getById(id, userId) ?: return@withTransaction
            val loan = loanDao.getById(payment.loanId, userId) ?: return@withTransaction
            val paymentTransaction = transactionDao.getById(payment.transactionId, userId)
            transactionDao.softDeleteById(payment.transactionId, userId, now)
            if (paymentTransaction != null) {
                pendingTransactionOps += paymentTransaction.copy(
                    syncId = paymentTransaction.syncId ?: UUID.randomUUID().toString(),
                    deletedAt = now,
                    updatedAt = now
                ) to SyncOperation.DELETE
            }
            loanPaymentDao.softDeleteById(id, userId, now)
            pendingLoanPaymentOps += payment.copy(
                syncId = payment.syncId ?: UUID.randomUUID().toString(),
                deletedAt = now,
                updatedAt = now
            ) to SyncOperation.DELETE

            val newAmountRepaid = loan.amountRepaid - payment.amount
            val newStatus = computeLoanStatus(loan.amount, newAmountRepaid, loan.startDate, loan.dueDate, now)
            val updatedLoan = loan.copy(
                amountRepaid = newAmountRepaid,
                remainingAmount = loan.amount - newAmountRepaid,
                status = newStatus,
                updatedAt = now,
                syncId = loan.syncId ?: UUID.randomUUID().toString()
            )
            loanDao.upsert(updatedLoan)
            pendingLoanOps += updatedLoan to SyncOperation.UPDATE
        }

        pendingTransactionOps.forEach { (entity, operation) -> transactionSyncEnqueuer.enqueue(entity, operation) }
        pendingLoanOps.forEach { (entity, operation) -> loanSyncEnqueuer.enqueueLoan(entity, operation) }
        pendingLoanPaymentOps.forEach { (entity, operation) -> loanSyncEnqueuer.enqueueLoanPayment(entity, operation) }
    }

    override suspend fun findLoanIdForTransaction(transactionId: Long): Long? = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        loanDao.findIdByTransactionId(transactionId, userId)
            ?: loanPaymentDao.findLoanIdByTransactionId(transactionId, userId)
    }

    /**
     * Retrouve l'une des 4 catégories par défaut Prêts/Emprunts par son nom exact, et la RECRÉE
     * silencieusement si l'utilisateur l'a supprimée entre-temps — un prêt/emprunt ne peut pas
     * fonctionner sans catégorie (voir [com.arzikina.ne.domain.model.Transaction.categoryId],
     * toujours renseignée pour un revenu ou une dépense). Délègue à [SystemCategoryResolver],
     * partagé avec `TransactionRepositoryImpl` (fonctionnalité Frais) : comportement inchangé,
     * seule la logique commune a été extraite pour ne pas la dupliquer.
     */
    private suspend fun resolveLoanCategory(name: String, userId: Long): CategoryEntity =
        SystemCategoryResolver.resolve(categoryDao, categorySyncEnqueuer, name, userId)

    private fun disbursementTransactionType(loanType: LoanType): TransactionType =
        if (loanType == LoanType.LENT) TransactionType.EXPENSE else TransactionType.INCOME

    private fun disbursementCategoryName(loanType: LoanType): String =
        if (loanType == LoanType.LENT) LoanCategoryNames.DISBURSEMENT_LENT else LoanCategoryNames.DISBURSEMENT_BORROWED

    private fun repaymentTransactionType(loanType: LoanType): TransactionType =
        if (loanType == LoanType.LENT) TransactionType.INCOME else TransactionType.EXPENSE

    private fun repaymentCategoryName(loanType: LoanType): String =
        if (loanType == LoanType.LENT) LoanCategoryNames.REPAYMENT_LENT else LoanCategoryNames.REPAYMENT_BORROWED

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
