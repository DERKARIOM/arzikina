package com.arzikina.ne.data.repository

import com.arzikina.ne.data.local.dao.AccountDao
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.Account
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

    override suspend fun saveAccount(account: Account) =
        withContext(ioDispatcher) { accountDao.upsert(account.toEntity(requireCurrentUserId())) }

    override suspend fun deleteAccount(id: Long) =
        withContext(ioDispatcher) { accountDao.deleteById(id, requireCurrentUserId()) }

    /** Les écrans qui appellent ce repository ne sont accessibles qu'à un utilisateur connecté. */
    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
