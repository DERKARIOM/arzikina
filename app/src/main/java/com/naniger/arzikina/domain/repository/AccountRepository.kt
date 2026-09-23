package com.naniger.arzikina.domain.repository

import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.CardSecrets
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

    /** Flux réactif de tous les comptes, triés par [Account.displayOrder] (voir sa doc). */
    fun observeAccounts(): Flow<List<Account>>

    suspend fun getAccount(id: Long): Account?

    /** Crée le compte si [Account.id] vaut 0, le met à jour sinon. Retourne l'id définitif du
     * compte (celui généré à la création, ou [Account.id] inchangé pour une mise à jour) — utile
     * notamment pour associer un [CardSecrets] à un compte tout juste créé (voir [saveCardSecrets]). */
    suspend fun saveAccount(account: Account): Long

    suspend fun deleteAccount(id: Long)

    /**
     * Persiste un nouvel ordre d'affichage après un glisser-déposer sur l'écran "Comptes" (voir
     * `presentation/accounts/AccountsFragment`, onglets Comptes ET Cartes bancaires — jamais
     * Planification, qui ne partage ni le même adaptateur ni la même entité, voir sa doc).
     *
     * [orderedIds] : TOUS les comptes actuellement visibles dans la sous-liste réordonnée, dans
     * leur position finale après le dépôt (pas seulement celui déplacé) — réattribue une position
     * `0..N-1` strictement dans cet ordre. Seuls les comptes dont la position a RÉELLEMENT changé
     * sont réécrits et synchronisés (voir l'implémentation) : pas de recompactage systématique de
     * toute la liste à chaque appel.
     */
    suspend fun reorderAccounts(orderedIds: List<Long>)

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
