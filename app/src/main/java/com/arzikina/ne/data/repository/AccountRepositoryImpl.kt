package com.arzikina.ne.data.repository

import androidx.room.withTransaction
import com.arzikina.ne.data.local.dao.AccountDao
import com.arzikina.ne.data.local.dao.CardSecretDao
import com.arzikina.ne.data.local.dao.LoanDao
import com.arzikina.ne.data.local.dao.LoanPaymentDao
import com.arzikina.ne.data.local.dao.TransactionDao
import com.arzikina.ne.data.local.database.ArzikinaDatabase
import com.arzikina.ne.data.local.entity.AccountEntity
import com.arzikina.ne.data.local.entity.CardSecretEntity
import com.arzikina.ne.data.local.entity.TransactionEntity
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.data.remote.dto.AccountSyncPayload
import com.arzikina.ne.data.security.CardCipher
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.CardSecrets
import com.arzikina.ne.domain.model.SyncOperation
import com.arzikina.ne.domain.model.computeLoanStatus
import com.arzikina.ne.domain.repository.AccountRepository
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
    private val syncQueueEnqueuer: SyncQueueEnqueuer,
    private val transactionSyncEnqueuer: TransactionSyncEnqueuer,
    private val json: Json,
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

    /**
     * [AccountEntity.syncId]/[AccountEntity.version] PRÉSERVÉS d'une modification à l'autre (jamais
     * réinitialisés par `toEntity`, qui les ignore volontairement) — même raisonnement que
     * `CategoryRepositoryImpl.saveCategory`/`PersonRepositoryImpl.savePerson` (voir leur KDoc pour le
     * détail complet). [Account] (voir sa définition) ne porte pas d'`updatedAt` : recalculé ici à
     * chaque enregistrement, comme pour `Person`.
     */
    override suspend fun saveAccount(account: Account): Long = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val existing = if (account.id != 0L) accountDao.getById(account.id, userId) else null
        val now = System.currentTimeMillis()

        val entity = account.toEntity(userId).copy(
            syncId = existing?.syncId ?: UUID.randomUUID().toString(),
            updatedAt = now,
            deletedAt = existing?.deletedAt,
            version = existing?.version ?: 1
        )
        val generatedId = accountDao.upsert(entity)
        // @Upsert ne retourne l'id généré QUE pour une insertion réelle (nouveau compte, id == 0) ;
        // pour une mise à jour, il retourne -1 (voir Account.id, déjà connu dans ce cas).
        val savedId = if (account.id != 0L) account.id else generatedId
        enqueueAccountSync(
            entity.copy(id = savedId),
            operation = if (existing == null) SyncOperation.CREATE else SyncOperation.UPDATE
        )
        savedId
    }

    /**
     * Supprime un compte.
     *
     * NE REPOSE PLUS sur les `ForeignKey(onDelete = CASCADE)` de `accountId`/`transferAccountId`
     * (`loans`, `loan_payments`, `transactions` — voir [com.arzikina.ne.data.local.entity.LoanEntity]/
     * [com.arzikina.ne.data.local.entity.LoanPaymentEntity]/[com.arzikina.ne.data.local.entity.TransactionEntity]) :
     * toutes les lignes concernées sont supprimées EXPLICITEMENT ci-dessous, dans la MÊME transaction
     * Room que la suppression douce du compte lui-même ([AccountDao.softDeleteById], depuis l'étape
     * 16.3 — voir `AccountDao` pour le raisonnement) — une simple `UPDATE` ne déclenche AUCUNE
     * `CASCADE` SQLite, contrairement à l'ancienne `DELETE`. Les contraintes `CASCADE` restent
     * déclarées en base à titre de filet de sécurité (une ligne oubliée ici serait quand même purgée
     * si elle redevenait un jour une vraie suppression), mais le code ne doit plus JAMAIS en dépendre
     * pour être correct.
     *
     * 1. Prêts/emprunts dont CE compte est le compte PRINCIPAL : leurs transactions liées
     *    (décaissement + remboursements, même sur un AUTRE compte encore existant), leurs lignes
     *    `loan_payments`, puis le prêt lui-même sont supprimés explicitement.
     * 2. Remboursements enregistrés SUR ce compte pour un prêt/emprunt dont le compte principal est
     *    DIFFÉRENT : le prêt parent est recalculé (montant remboursé/solde restant/statut) AVANT que
     *    sa transaction et sa ligne `loan_payments` ne soient supprimées.
     * 3. Transactions liées à des FRAIS (voir [com.arzikina.ne.domain.model.Transaction.feeTransactionId]),
     *    dans les deux sens, pour toute transaction sur le point de disparaître à cause de ce compte
     *    (`accountId` OU `transferAccountId`, voir [cleanUpFeeLinksBeforeAccountCascade]) :
     *    - elle est elle-même une transaction PARENTE avec des frais liés sur un AUTRE compte
     *      (survivant) : sa transaction de frais doit disparaître avec elle, comme le ferait
     *      [com.arzikina.ne.data.repository.TransactionRepositoryImpl.deleteTransaction] pour une
     *      suppression explicite — sinon cette ligne de frais devient orpheline et réapparaît comme
     *      une transaction "Frais et commissions" ordinaire, sans lien avec rien.
     *    - elle est elle-même une transaction de FRAIS référencée par une transaction PARENTE sur
     *      un AUTRE compte (survivante) : le pointeur `feeTransactionId` de cette dernière
     *      deviendrait mort (SQLite ne le sait pas, cette colonne n'a volontairement pas de
     *      `ForeignKey`, voir `TransactionEntity`) — neutralisé au lieu d'être laissé pendant.
     * 4. Toutes les transactions restantes référençant ce compte (`accountId` OU
     *    `transferAccountId`) sont supprimées explicitement (voir [cleanUpFeeLinksBeforeAccountCascade],
     *    qui retourne désormais ce lot). Les transactions déjà traitées aux points 1/2 y
     *    réapparaissent (même compte) : explicitement EXCLUES via `alreadyHandledIds` avant ce lot,
     *    pour ne jamais les enfiler DEUX fois en synchronisation (voir [softDeleteAndEnqueue]).
     *
     * Chaque suppression de transaction ci-dessus est désormais DOUCE ([softDeleteAndEnqueue],
     * `TransactionDao.softDeleteById`) et enfilée pour la synchronisation (étape 17, voir
     * `TransactionSyncEnqueuer`) — plus une suppression physique silencieuse.
     *
     * Duplique volontairement une partie de la logique de [com.arzikina.ne.data.repository.LoanRepositoryImpl.deleteLoan]/
     * `.deletePayment` plutôt que d'en dépendre : un repository ne doit pas dépendre d'un autre
     * repository pour rester libre de composer plusieurs DAO dans une seule transaction Room (voir
     * la doc de `LoanRepositoryImpl`).
     */
    override suspend fun deleteAccount(id: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val existing = accountDao.getById(id, userId) ?: return@withContext
        val now = System.currentTimeMillis()
        // Toute transaction Arzikina purgée en cascade ci-dessous doit désormais être enfilée
        // (étape 17 : `Transaction` est une entité synchronisée) — voir `TransactionSyncEnqueuer`.
        // Enfilée APRÈS le `database.withTransaction`, jamais dedans (même principe que
        // `TransactionRepositoryImpl`/`LoanRepositoryImpl`).
        val pendingSyncOps = mutableListOf<Pair<TransactionEntity, SyncOperation>>()

        database.withTransaction {
            val disappearingTransactions = cleanUpFeeLinksBeforeAccountCascade(id, userId, now, pendingSyncOps)

            loanDao.getAllForAccount(id, userId).forEach { loan ->
                loanPaymentDao.getAllForLoan(loan.id, userId).forEach { payment ->
                    softDeleteAndEnqueue(payment.transactionId, userId, now, pendingSyncOps)
                    loanPaymentDao.deleteById(payment.id, userId)
                }
                softDeleteAndEnqueue(loan.transactionId, userId, now, pendingSyncOps)
                loanDao.deleteById(loan.id, userId)
            }

            loanPaymentDao.getAllForAccount(id, userId).forEach { payment ->
                val loan = loanDao.getById(payment.loanId, userId) ?: return@forEach
                // Déjà traité ci-dessus (le prêt lui-même disparaît avec ce compte) : pas besoin de
                // le recalculer, il n'existera plus.
                if (loan.accountId == id) return@forEach

                softDeleteAndEnqueue(payment.transactionId, userId, now, pendingSyncOps)
                val recalcNow = System.currentTimeMillis()
                val newAmountRepaid = (loan.amountRepaid - payment.amount).coerceAtLeast(0L)
                val newStatus = computeLoanStatus(loan.amount, newAmountRepaid, loan.startDate, loan.dueDate, recalcNow)
                loanDao.upsert(
                    loan.copy(
                        amountRepaid = newAmountRepaid,
                        remainingAmount = loan.amount - newAmountRepaid,
                        status = newStatus,
                        updatedAt = recalcNow
                    )
                )
                loanPaymentDao.deleteById(payment.id, userId)
            }

            // Certaines de ces transactions ont déjà été traitées ci-dessus (même compte, voir la
            // doc de [cleanUpFeeLinksBeforeAccountCascade]) : ne jamais les enfiler DEUX fois.
            val alreadyHandledIds = pendingSyncOps.map { it.first.id }.toSet()
            disappearingTransactions.forEach { transaction ->
                if (transaction.id in alreadyHandledIds) return@forEach
                softDeleteAndEnqueue(transaction.id, userId, now, pendingSyncOps, prefetched = transaction)
            }

            accountDao.softDeleteById(id, userId, now)
        }

        pendingSyncOps.forEach { (entity, operation) -> transactionSyncEnqueuer.enqueue(entity, operation) }
        enqueueAccountSync(
            existing.copy(syncId = existing.syncId ?: UUID.randomUUID().toString(), deletedAt = now, updatedAt = now),
            operation = SyncOperation.DELETE
        )
    }

    /**
     * Suppression DOUCE d'une transaction ([TransactionDao.softDeleteById], remplace l'ancien
     * `deleteById` — voir la doc de tête de [deleteAccount]) + accumulation dans [pendingSyncOps]
     * pour l'enfilage `DELETE` après la transaction Room. [prefetched] évite une lecture redondante
     * quand l'appelant a déjà la ligne sous la main (voir [cleanUpFeeLinksBeforeAccountCascade]).
     * Ne fait rien si la ligne n'existe déjà plus (filet de sécurité, ne devrait pas arriver).
     */
    private suspend fun softDeleteAndEnqueue(
        transactionId: Long,
        userId: Long,
        now: Long,
        pendingSyncOps: MutableList<Pair<TransactionEntity, SyncOperation>>,
        prefetched: TransactionEntity? = null
    ) {
        val transaction = prefetched ?: transactionDao.getById(transactionId, userId) ?: return
        transactionDao.softDeleteById(transactionId, userId, now)
        pendingSyncOps += transaction.copy(
            syncId = transaction.syncId ?: UUID.randomUUID().toString(),
            deletedAt = now,
            updatedAt = now
        ) to SyncOperation.DELETE
    }

    /**
     * Voir le point 3 de la doc de [deleteAccount]. Rassemble toute transaction sur le point de
     * disparaître à cause de CE compte — `accountId` (compte principal) ET `transferAccountId`
     * (compte destination d'un virement, voir `TransactionEntity`), dédupliquée par id (les deux
     * colonnes ne peuvent normalement pas désigner le même compte pour une même ligne) — neutralise
     * les deux sens du lien de frais (suppression DOUCE + enfilage pour la transaction de frais sur
     * un AUTRE compte, voir [softDeleteAndEnqueue]), puis RETOURNE ce lot pour que [deleteAccount]
     * le supprime explicitement (point 4) plutôt que de compter sur une `CASCADE` SQL — inopérante
     * de toute façon depuis que la suppression du compte est une suppression DOUCE.
     */
    private suspend fun cleanUpFeeLinksBeforeAccountCascade(
        accountId: Long,
        userId: Long,
        now: Long,
        pendingSyncOps: MutableList<Pair<TransactionEntity, SyncOperation>>
    ): List<TransactionEntity> {
        val disappearing = (
            transactionDao.getAllForAccount(accountId, userId) +
                transactionDao.getAllForTransferAccount(accountId, userId)
            ).distinctBy { it.id }
        val disappearingIds = disappearing.map { it.id }.toSet()

        disappearing.forEach { transaction ->
            // Cette transaction est elle-même PARENTE de frais sur un AUTRE compte (survivant) :
            // sa transaction de frais doit disparaître avec elle. Si cette dernière disparaît de
            // toute façon dans ce même lot (déjà dans `disappearingIds`), rien à faire de plus (elle
            // sera traitée par le lot lui-même, voir l'appelant).
            transaction.feeTransactionId?.let { feeTransactionId ->
                if (feeTransactionId !in disappearingIds) {
                    softDeleteAndEnqueue(feeTransactionId, userId, now, pendingSyncOps)
                }
            }
            // Cette transaction est peut-être elle-même une ligne de FRAIS référencée par une
            // transaction PARENTE survivante ailleurs : neutraliser ce pointeur avant qu'il ne
            // devienne mort.
            transactionDao.clearFeeTransactionReference(transaction.id, userId)
        }

        return disappearing
    }

    private suspend fun enqueueAccountSync(entity: AccountEntity, operation: SyncOperation) {
        val payload = AccountSyncPayload(
            id = requireNotNull(entity.syncId) { "syncId doit être généré avant l'enfilage." },
            baseVersion = if (operation == SyncOperation.CREATE) null else entity.version,
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
            operation = operation,
            payloadJson = json.encodeToString(AccountSyncPayload.serializer(), payload)
        )
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
