package com.arzikina.ne.util

import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Transaction

/**
 * Point d'entrée UNIQUE de la notion « un compte compte-t-il dans les statistiques
 * personnelles de l'utilisateur ? » (voir [Account.isExcludedFromStatistics]).
 *
 * Centralisé ici plutôt que dupliqué : chaque écran qui agrège un montant à travers
 * PLUSIEURS comptes (solde total du Dashboard, revenus/dépenses, répartition par
 * catégorie, évolution mensuelle, progression des budgets...) doit construire son
 * périmètre via [scope] AVANT tout calcul, au lieu de tester
 * `account.isExcludedFromStatistics` directement à chaque endroit (voir instructions
 * projet : « évite absolument le code dupliqué »).
 *
 * Ne concerne QUE les agrégations MULTI-comptes. Le solde propre d'UN SEUL compte
 * (écran Détail du compte, liste des comptes, sélecteur de compte dans les
 * formulaires) reste calculé sur la totalité de ses transactions, exclu ou non — voir
 * [com.arzikina.ne.presentation.accounts.computeCurrentBalances], qui n'est PAS
 * concerné par cet objet : un compte exclu des statistiques reste pleinement
 * fonctionnel et continue d'afficher son solde réel.
 */
object PersonalStatistics {

    data class Scope(val accounts: List<Account>, val transactions: List<Transaction>)

    /**
     * [Scope.accounts] : uniquement les comptes dont [Account.isExcludedFromStatistics]
     * est `false`.
     *
     * [Scope.transactions] : uniquement les transactions dont le compte SOURCE
     * ([Transaction.accountId]) fait partie de [Scope.accounts]. Aucun traitement
     * spécial n'est nécessaire pour les transferts
     * ([com.arzikina.ne.domain.model.TransactionType.TRANSFER]) entre un compte
     * inclus et un compte exclu : les écrans de statistiques (Dashboard, Statistiques,
     * Budgets) filtrent déjà exclusivement sur INCOME/EXPENSE pour leurs totaux de
     * revenus/dépenses, donc un TRANSFER n'y entre jamais, qu'il touche un compte
     * exclu ou non.
     */
    fun scope(accounts: List<Account>, transactions: List<Transaction>): Scope {
        val includedAccounts = accounts.filter { !it.isExcludedFromStatistics }
        val includedAccountIds = includedAccounts.map { it.id }.toSet()
        val includedTransactions = transactions.filter { it.accountId in includedAccountIds }
        return Scope(includedAccounts, includedTransactions)
    }
}
