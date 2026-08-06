package com.arzikina.ne.data.repository

import com.arzikina.ne.data.local.dao.AccountDao
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.repository.AccountRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implémentation Room de [AccountRepository].
 *
 * Toutes les opérations de lecture/écriture sont exécutées sur
 * [IoDispatcher] : les ViewModels appelants n'ont pas à s'en soucier et
 * restent testables avec un dispatcher de test injecté à la place.
 */
class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AccountRepository {

    override fun observeAccounts(): Flow<List<Account>> =
        accountDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAccount(id: Long): Account? =
        withContext(ioDispatcher) { accountDao.getById(id)?.toDomain() }

    override suspend fun saveAccount(account: Account) =
        withContext(ioDispatcher) { accountDao.upsert(account.toEntity()) }

    override suspend fun deleteAccount(id: Long) =
        withContext(ioDispatcher) { accountDao.deleteById(id) }
}
