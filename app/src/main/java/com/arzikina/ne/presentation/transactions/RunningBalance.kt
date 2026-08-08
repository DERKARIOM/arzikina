package com.arzikina.ne.presentation.transactions

import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.model.signedAmount

/**
 * Calcule, pour chaque transaction, le solde du compte auquel elle appartient
 * juste après son passage — affiché entre parenthèses sous le montant (voir
 * [TransactionItemBinder]). Le calcul remonte depuis le solde ACTUEL de
 * chaque compte plutôt que d'accumuler depuis le solde initial : plus simple,
 * car [transactions] est déjà trié du plus récent au plus ancien (voir
 * [com.arzikina.ne.domain.repository.TransactionRepository.observeTransactions]),
 * l'ordre naturel pour "défaire" les mouvements un par un.
 *
 * Volontairement calculé sur [transactions] NON filtrées (avant recherche/
 * filtres de l'écran Transactions) : une transaction masquée par un filtre
 * doit quand même être prise en compte dans l'historique des soldes des
 * transactions voisines encore affichées, sous peine de soldes historiques
 * incohérents dès qu'un filtre est actif.
 *
 * Partagé entre l'écran Transactions ([TransactionsViewModel]) et "Détail du
 * compte" ([com.arzikina.ne.presentation.accounts.AccountDetailViewModel]).
 */
fun computeRunningBalances(transactions: List<Transaction>, accounts: List<Account>): Map<Long, Long> {
    val accountsById = accounts.associateBy { it.id }
    val result = mutableMapOf<Long, Long>()

    transactions.groupBy { it.accountId }.forEach { (accountId, accountTransactions) ->
        val account = accountsById[accountId] ?: return@forEach
        var balance = account.initialBalance + accountTransactions.sumOf { it.signedAmount() }
        // accountTransactions conserve l'ordre de [transactions] : plus récent au plus ancien.
        accountTransactions.forEach { transaction ->
            result[transaction.id] = balance
            balance -= transaction.signedAmount()
        }
    }
    return result
}
