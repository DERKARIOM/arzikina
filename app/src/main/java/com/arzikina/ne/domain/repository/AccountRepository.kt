package com.arzikina.ne.domain.repository

import com.arzikina.ne.domain.model.Account
import kotlinx.coroutines.flow.Flow

/**
 * Contrat d'accès aux données des comptes.
 *
 * La couche presentation ne dépend que de cette interface, jamais de
 * l'implémentation Room (voir `data/repository/AccountRepositoryImpl`) :
 * cela permet de remplacer la source de données (ex. synchronisation cloud
 * future) sans toucher aux ViewModels.
 */
interface AccountRepository {

    /** Flux réactif de tous les comptes, triés par date de création. */
    fun observeAccounts(): Flow<List<Account>>

    suspend fun getAccount(id: Long): Account?

    /** Crée le compte si [Account.id] vaut 0, le met à jour sinon. */
    suspend fun saveAccount(account: Account)

    suspend fun deleteAccount(id: Long)
}
