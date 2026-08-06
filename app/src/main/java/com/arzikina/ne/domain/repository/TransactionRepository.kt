package com.arzikina.ne.domain.repository

import com.arzikina.ne.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Contrat d'accès aux données des transactions. Voir [AccountRepository] pour
 * le raisonnement derrière cette séparation (indépendance vis-à-vis de Room).
 *
 * Volontairement minimal pour l'instant : les besoins de filtrage/recherche
 * (par compte, par catégorie, par période, texte libre) seront ajoutés ici au
 * fur et à mesure des écrans qui en ont réellement besoin, plutôt
 * qu'anticipés sans consommateur concret.
 */
interface TransactionRepository {

    fun observeTransactions(): Flow<List<Transaction>>

    suspend fun getTransaction(id: Long): Transaction?

    /** Crée la transaction si [Transaction.id] vaut 0, la met à jour sinon. */
    suspend fun saveTransaction(transaction: Transaction)

    suspend fun deleteTransaction(id: Long)
}
