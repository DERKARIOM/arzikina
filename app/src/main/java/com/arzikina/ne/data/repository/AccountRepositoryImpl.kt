package com.arzikina.ne.data.repository

import androidx.room.withTransaction
import com.arzikina.ne.data.local.dao.AccountDao
import com.arzikina.ne.data.local.dao.CardSecretDao
import com.arzikina.ne.data.local.dao.LoanDao
import com.arzikina.ne.data.local.dao.LoanPaymentDao
import com.arzikina.ne.data.local.dao.TransactionDao
import com.arzikina.ne.data.local.database.ArzikinaDatabase
import com.arzikina.ne.data.local.entity.CardSecretEntity
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.data.security.CardCipher
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.CardSecrets
import com.arzikina.ne.domain.model.computeLoanStatus
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implémentation Room de [AccountRepository].
 *
 * Toutes les opérations de lecture/écriture sont exécutées sur
 * [IoDispatcher] : les ViewModels appelants n'ont pas à s'en soucier et
 * restent testables avec un dispatcher de test injecté à la place.
 *
 * Isolation multi-utilisateurs : ce repository résout lui-même l'utilisateur
 * courant via [SessionManager] avant chaque accès à [accountDao] — le
 * contrat [AccountRepository] (donc chaque ViewModel qui l'utilise) reste
 * INCHANGÉ, il n'a jamais besoin de connaître ni de transmettre un `userId`.
 * Voir [SessionManager] pour le raisonnement de cette séparation.
 */
class AccountRepositoryImpl @Inject constructor(
    private val database: ArzikinaDatabase,
    private val accountDao: AccountDao,
    private val cardSecretDao: CardSecretDao,
    private val loanDao: LoanDao,
    private val loanPaymentDao: LoanPaymentDao,
    private val transactionDao: TransactionDao,
    private val sessionManager: SessionManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AccountRepository {

    override fun observeAccounts(): Flow<List<Account>> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                accountDao.observeAllForUser(userId).map { entities -> entities.map { it.toDomain() } }
            }
        }

    override suspend fun getAccount(id: Long): Account? =
        withContext(ioDispatcher) { accountDao.getById(id, requireCurrentUserId())?.toDomain() }

    override suspend fun saveAccount(account: Account): Long = withContext(ioDispatcher) {
        val generatedId = accountDao.upsert(account.toEntity(requireCurrentUserId()))
        // @Upsert ne retourne l'id généré QUE pour une insertion réelle (nouveau compte, id == 0) ;
        // pour une mise à jour, il retourne -1 (voir Account.id, déjà connu dans ce cas).
        if (account.id != 0L) account.id else generatedId
    }

    /**
     * Supprime un compte. `accountId` est en `CASCADE` sur `loans` ET `loan_payments` (voir
     * [com.arzikina.ne.data.local.entity.LoanEntity]/[com.arzikina.ne.data.local.entity.LoanPaymentEntity]) :
     * cette suppression peut donc emporter des prêts/emprunts et des remboursements avec elle.
     * Nettoie explicitement ce que la cascade SQLite seule laisserait incohérent, dans la MÊME
     * transaction Room que la suppression elle-même :
     * 1. Prêts/emprunts dont CE compte est le compte PRINCIPAL : ils disparaissent en cascade —
     *    leurs transactions liées (décaissement + remboursements, même sur un AUTRE compte encore
     *    existant) doivent être supprimées explicitement, sinon elles deviennent orphelines
     *    (aucun prêt/versement ne les référence plus, mais elles restent visibles dans "Transactions").
     * 2. Remboursements enregistrés SUR ce compte pour un prêt/emprunt dont le compte principal est
     *    DIFFÉRENT : la ligne `loan_payments` disparaît en cascade sans que le prêt parent ne soit
     *    averti — son montant remboursé/solde restant/statut doivent être recalculés AVANT, sinon
     *    le prêt reste figé avec un montant remboursé qui ne correspond plus à aucun versement réel.
     *
     * Duplique volontairement une partie de la logique de [com.arzikina.ne.data.repository.LoanRepositoryImpl.deleteLoan]/
     * `.deletePayment` plutôt que d'en dépendre : un repository ne doit pas dépendre d'un autre
     * repository pour rester libre de composer plusieurs DAO dans une seule transaction Room (voir
     * la doc de `LoanRepositoryImpl`).
     */
    override suspend fun deleteAccount(id: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        database.withTransaction {
            loanDao.getAllForAccount(id, userId).forEach { loan ->
                loanPaymentDao.getAllForLoan(loan.id, userId).forEach { payment ->
                    transactionDao.deleteById(payment.transactionId, userId)
                }
                transactionDao.deleteById(loan.transactionId, userId)
            }

            loanPaymentDao.getAllForAccount(id, userId).forEach { payment ->
                val loan = loanDao.getById(payment.loanId, userId) ?: return@forEach
                // Déjà traité ci-dessus (le prêt lui-même disparaît en cascade avec ce compte) :
                // pas besoin de le recalculer, il n'existera plus.
                if (loan.accountId == id) return@forEach

                transactionDao.deleteById(payment.transactionId, userId)
                val now = System.currentTimeMillis()
                val newAmountRepaid = (loan.amountRepaid - payment.amount).coerceAtLeast(0L)
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

            accountDao.deleteById(id, userId)
        }
    }

    override suspend fun saveCardSecrets(accountId: Long, fullNumber: String, cvv: String) {
        withContext(ioDispatcher) {
            val encryptedNumber = CardCipher.encrypt(fullNumber)
            val encryptedCvv = CardCipher.encrypt(cvv)
            cardSecretDao.upsert(
                CardSecretEntity(
                    accountId = accountId,
                    cardNumberEncrypted = encryptedNumber.ciphertextBase64,
                    cardNumberIv = encryptedNumber.ivBase64,
                    cardCvvEncrypted = encryptedCvv.ciphertextBase64,
                    cardCvvIv = encryptedCvv.ivBase64
                )
            )
        }
    }

    override suspend fun revealCardSecrets(accountId: Long): CardSecrets? = withContext(ioDispatcher) {
        val secret = cardSecretDao.getByAccountId(accountId) ?: return@withContext null
        CardSecrets(
            fullNumber = CardCipher.decrypt(secret.cardNumberEncrypted, secret.cardNumberIv),
            cvv = CardCipher.decrypt(secret.cardCvvEncrypted, secret.cardCvvIv)
        )
    }

    /** Les écrans qui appellent ce repository ne sont accessibles qu'à un utilisateur connecté. */
    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
