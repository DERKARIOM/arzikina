package com.arzikina.ne.domain.repository

import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.CardSecrets
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

    /** Crée le compte si [Account.id] vaut 0, le met à jour sinon. Retourne l'id définitif du
     * compte (celui généré à la création, ou [Account.id] inchangé pour une mise à jour) — utile
     * notamment pour associer un [CardSecrets] à un compte tout juste créé (voir [saveCardSecrets]). */
    suspend fun saveAccount(account: Account): Long

    suspend fun deleteAccount(id: Long)

    /**
     * Chiffre et enregistre le numéro complet et le CVV d'une carte de crédit (voir
     * `data/security/CardCipher`) — écrase le secret déjà enregistré pour ce compte, s'il existe.
     * Sans effet sur [Account]/[getAccount] : voir la doc de `data/local/entity/CardSecretEntity`
     * pour le raisonnement de cette séparation.
     */
    suspend fun saveCardSecrets(accountId: Long, fullNumber: String, cvv: String)

    /** Déchiffre et retourne le numéro complet + CVV d'une carte, `null` si aucun secret n'est
     * enregistré pour ce compte (compte classique, ou carte créée avant cette fonctionnalité). */
    suspend fun revealCardSecrets(accountId: Long): CardSecrets?
}
