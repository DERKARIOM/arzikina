package com.arzikina.ne.data.repository

import com.arzikina.ne.data.local.dao.AccountDao
import com.arzikina.ne.data.local.dao.CardSecretDao
import com.arzikina.ne.data.local.entity.CardSecretEntity
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.data.security.CardCipher
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.CardSecrets
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
    private val accountDao: AccountDao,
    private val cardSecretDao: CardSecretDao,
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

    override suspend fun deleteAccount(id: Long) =
        withContext(ioDispatcher) { accountDao.deleteById(id, requireCurrentUserId()) }

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
