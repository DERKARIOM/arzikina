package com.arzikina.ne.data.repository

import androidx.room.withTransaction
import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.local.dao.LoanDao
import com.arzikina.ne.data.local.dao.LoanPaymentDao
import com.arzikina.ne.data.local.dao.TransactionDao
import com.arzikina.ne.data.local.database.ArzikinaDatabase
import com.arzikina.ne.data.local.database.SystemCategoryResolver
import com.arzikina.ne.data.local.entity.CategoryEntity
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.Loan
import com.arzikina.ne.domain.model.LoanCategoryNames
import com.arzikina.ne.domain.model.LoanPayment
import com.arzikina.ne.domain.model.LoanType
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
 * qui suit le même principe.
 */
class LoanRepositoryImpl @Inject constructor(
    private val database: ArzikinaDatabase,
    private val loanDao: LoanDao,
    private val loanPaymentDao: LoanPaymentDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val sessionManager: SessionManager,
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
        database.withTransaction {
            if (loan.id == 0L) {
                val now = System.currentTimeMillis()
                val category = resolveLoanCategory(disbursementCategoryName(loan.type), userId)
                val transactionId = transactionDao.upsert(
                    Transaction(
                        amount = loan.amount,
                        type = disbursementTransactionType(loan.type),
                        accountId = loan.accountId,
                        categoryId = category.id,
                        date = loan.startDate,
                        description = loan.description.ifBlank { category.name },
                        createdAt = now
                    ).toEntity(userId)
                )
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
                    transactionDao.upsert(
                        existingTransaction.copy(
                            amount = loan.amount,
                            accountId = loan.accountId,
                            categoryId = category.id,
                            date = loan.startDate,
                            description = loan.description.ifBlank { category.name }
                        )
                    )
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
    }

    override suspend fun deleteLoan(id: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        database.withTransaction {
            val loan = loanDao.getById(id, userId) ?: return@withTransaction
            loanPaymentDao.getAllForLoan(id, userId).forEach { payment ->
                transactionDao.deleteById(payment.transactionId, userId)
            }
            transactionDao.deleteById(loan.transactionId, userId)
            // Supprime aussi, en cascade SQLite, toutes les lignes loan_payments de ce prêt/emprunt.
            loanDao.deleteById(id, userId)
        }
    }

    override suspend fun recordPayment(payment: LoanPayment): Long = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        database.withTransaction {
            val loan = loanDao.getById(payment.loanId, userId) ?: error("Prêt/emprunt introuvable.")
            check(payment.amount in 1..loan.remainingAmount) {
                "Le montant du remboursement dépasse le solde restant du prêt/emprunt."
            }
            val now = System.currentTimeMillis()
            val category = resolveLoanCategory(repaymentCategoryName(loan.type), userId)
            val transactionId = transactionDao.upsert(
                Transaction(
                    amount = payment.amount,
                    type = repaymentTransactionType(loan.type),
                    accountId = payment.accountId,
                    categoryId = category.id,
                    date = payment.date,
                    description = payment.note.ifBlank { category.name },
                    createdAt = now
                ).toEntity(userId)
            )
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
    }

    override suspend fun deletePayment(id: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        database.withTransaction {
            val payment = loanPaymentDao.getById(id, userId) ?: return@withTransaction
            val loan = loanDao.getById(payment.loanId, userId) ?: return@withTransaction
            transactionDao.deleteById(payment.transactionId, userId)
            loanPaymentDao.deleteById(id, userId)
            val now = System.currentTimeMillis()
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
