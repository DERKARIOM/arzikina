package com.arzikina.ne.data.repository

import androidx.room.withTransaction
import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.local.dao.LoanDao
import com.arzikina.ne.data.local.dao.LoanPaymentDao
import com.arzikina.ne.data.local.dao.TransactionDao
import com.arzikina.ne.data.local.database.ArzikinaDatabase
import com.arzikina.ne.data.local.database.SystemCategoryResolver
import com.arzikina.ne.data.local.entity.CategoryEntity
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
 * qui suit le même principe. [TransactionSyncEnqueuer] fait exception (voir sa KDoc de tête) :
 * CE repository est l'un des CINQ qui écrivent des transactions, donc l'un des CINQ à l'injecter,
 * mais reste seul maître de `LoanDao`/`LoanPaymentDao` (jamais de `LoanRepository` externe).
 *
 * `Loan`/`LoanPayment` NE SONT PAS des entités synchronisées à cette étape (voir
 * `docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md`) — seules les transactions Arzikina qu'ils génèrent le
 * sont (étape 17). Les enfilages ci-dessous ne concernent donc QUE `transactionDao`, jamais
 * `loanDao`/`loanPaymentDao`, et ont lieu APRÈS le `database.withTransaction`, jamais dedans (même
 * principe que `TransactionRepositoryImpl`).
 */
class LoanRepositoryImpl @Inject constructor(
    private val database: ArzikinaDatabase,
    private val loanDao: LoanDao,
    private val loanPaymentDao: LoanPaymentDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val sessionManager: SessionManager,
    private val transactionSyncEnqueuer: TransactionSyncEnqueuer,
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
        val pendingSyncOps = mutableListOf<Pair<TransactionEntity, SyncOperation>>()

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
                pendingSyncOps += transactionEntity.copy(id = transactionId) to SyncOperation.CREATE

                val status = computeLoanStatus(loan.amount, 0L, loan.startDate, loan.dueDate, now)
                loanDao.upsert(
                    loan.copy(
                        amountRepaid = 0L,
                        remainingAmount = loan.amount,
                        status = status,
                        transactionId = transactionId,
                        createdAt = now,
                        updatedAt = now
                    ).toEntity(userId)
                )
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
                    pendingSyncOps += updatedTransaction to SyncOperation.UPDATE
                }
                loanDao.upsert(
                    loan.copy(
                        amountRepaid = existing.amountRepaid,
                        remainingAmount = loan.amount - existing.amountRepaid,
                        status = status,
                        transactionId = existing.transactionId,
                        updatedAt = now
                    ).toEntity(userId)
                )
                loan.id
            }
        }

        pendingSyncOps.forEach { (entity, operation) -> transactionSyncEnqueuer.enqueue(entity, operation) }
        savedId
    }

    override suspend fun deleteLoan(id: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val pendingSyncOps = mutableListOf<Pair<TransactionEntity, SyncOperation>>()
        val now = System.currentTimeMillis()

        database.withTransaction {
            val loan = loanDao.getById(id, userId) ?: return@withTransaction
            loanPaymentDao.getAllForLoan(id, userId).forEach { payment ->
                val paymentTransaction = transactionDao.getById(payment.transactionId, userId)
                transactionDao.softDeleteById(payment.transactionId, userId, now)
                if (paymentTransaction != null) {
                    pendingSyncOps += paymentTransaction.copy(
                        syncId = paymentTransaction.syncId ?: UUID.randomUUID().toString(),
                        deletedAt = now,
                        updatedAt = now
                    ) to SyncOperation.DELETE
                }
            }
            val loanTransaction = transactionDao.getById(loan.transactionId, userId)
            transactionDao.softDeleteById(loan.transactionId, userId, now)
            if (loanTransaction != null) {
                pendingSyncOps += loanTransaction.copy(
                    syncId = loanTransaction.syncId ?: UUID.randomUUID().toString(),
                    deletedAt = now,
                    updatedAt = now
                ) to SyncOperation.DELETE
            }
            // Supprime aussi, en cascade SQLite, toutes les lignes loan_payments de ce prêt/emprunt
            // (relation `loanId`, non touchée par cette étape — voir la KDoc de tête de cette classe :
            // `Loan`/`LoanPayment` ne sont pas des entités synchronisées).
            loanDao.deleteById(id, userId)
        }

        pendingSyncOps.forEach { (entity, operation) -> transactionSyncEnqueuer.enqueue(entity, operation) }
    }

    override suspend fun recordPayment(payment: LoanPayment): Long = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val pendingSyncOps = mutableListOf<Pair<TransactionEntity, SyncOperation>>()

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
            pendingSyncOps += transactionEntity.copy(id = transactionId) to SyncOperation.CREATE

            val newAmountRepaid = loan.amountRepaid + payment.amount
            val newStatus = computeLoanStatus(loan.amount, newAmountRepaid, loan.startDate, loan.dueDate, now)
            loanDao.upsert(
                loan.copy(
                    amountRepaid = newAmountRepaid,
                    remainingAmount = loan.amount - newAmountRepaid,
                    status = newStatus,
                    updatedAt = now
                )
            )
            loanPaymentDao.upsert(payment.copy(transactionId = transactionId, createdAt = now).toEntity(userId))
        }

        pendingSyncOps.forEach { (entity, operation) -> transactionSyncEnqueuer.enqueue(entity, operation) }
        result
    }

    override suspend fun deletePayment(id: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val pendingSyncOps = mutableListOf<Pair<TransactionEntity, SyncOperation>>()
        val now = System.currentTimeMillis()

        database.withTransaction {
            val payment = loanPaymentDao.getById(id, userId) ?: return@withTransaction
            val loan = loanDao.getById(payment.loanId, userId) ?: return@withTransaction
            val paymentTransaction = transactionDao.getById(payment.transactionId, userId)
            transactionDao.softDeleteById(payment.transactionId, userId, now)
            if (paymentTransaction != null) {
                pendingSyncOps += paymentTransaction.copy(
                    syncId = paymentTransaction.syncId ?: UUID.randomUUID().toString(),
                    deletedAt = now,
                    updatedAt = now
                ) to SyncOperation.DELETE
            }
            loanPaymentDao.deleteById(id, userId)
            val newAmountRepaid = loan.amountRepaid - payment.amount
            val newStatus = computeLoanStatus(loan.amount, newAmountRepaid, loan.startDate, loan.dueDate, now)
            loanDao.upsert(
                loan.copy(
                    amountRepaid = newAmountRepaid,
                    remainingAmount = loan.amount - newAmountRepaid,
                    status = newStatus,
                    updatedAt = now
                )
            )
        }

        pendingSyncOps.forEach { (entity, operation) -> transactionSyncEnqueuer.enqueue(entity, operation) }
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
        SystemCategoryResolver.resolve(categoryDao, name, userId)

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
