package com.arzikina.ne.presentation.transactions

import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.model.signedAmount

/**
 * Calcule, pour chaque (transaction, compte concerné), le solde de CE compte
 * juste après le passage de la transaction — affiché entre parenthèses sous
 * le montant (voir [TransactionItemBinder]). Clé composite plutôt que
 * simplement l'id de la transaction : un [TransactionType.TRANSFER] concerne
 * DEUX comptes (source ET destination, voir [Transaction.transferAccountId])
 * qui ont chacun un solde différent après son passage — même raisonnement
 * que [com.arzikina.ne.presentation.accounts.computeCurrentBalances], qui est
 * le seul autre endroit à devoir connaître les deux jambes d'un transfert.
 *
 * Le calcul remonte depuis le solde ACTUEL de chaque compte plutôt que
 * d'accumuler depuis le solde initial : plus simple, car [transactions] est
 * déjà trié du plus récent au plus ancien (voir
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
fun computeRunningBalances(transactions: List<Transaction>, accounts: List<Account>): Map<Pair<Long, Long>, Long> {
    val accountsById = accounts.associateBy { it.id }
    val result = mutableMapOf<Pair<Long, Long>, Long>()

    // Pour chaque compte concerné, la liste des (transaction, montant signé POUR CE COMPTE) —
    // une même transaction apparaît dans deux listes pour un transfert (source ET destination).
    val entriesByAccount = mutableMapOf<Long, MutableList<Pair<Transaction, Long>>>()
    transactions.forEach { transaction ->
        entriesByAccount.getOrPut(transaction.accountId) { mutableListOf() }
            .add(transaction to transaction.signedAmount())
        if (transaction.type == TransactionType.TRANSFER) {
            val destinationId = transaction.transferAccountId ?: return@forEach
            entriesByAccount.getOrPut(destinationId) { mutableListOf() }
                .add(transaction to transaction.amount)
        }
    }

    entriesByAccount.forEach { (accountId, entries) ->
        val account = accountsById[accountId] ?: return@forEach
        var balance = account.initialBalance + entries.sumOf { it.second }
        // entries conserve l'ordre de [transactions] : plus récent au plus ancien.
        entries.forEach { (transaction, signedAmountForAccount) ->
            result[transaction.id to accountId] = balance
            balance -= signedAmountForAccount
        }
    }
    return result
}
