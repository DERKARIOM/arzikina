package com.arzikina.ne.presentation.accounts

import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.model.signedAmount

/**
 * Solde COURANT de chaque compte (solde initial + somme signée de ses
 * transactions, voir [Transaction.signedAmount]) — calcul partagé par
 * [AccountsViewModel], [AccountDetailViewModel] et
 * [com.arzikina.ne.presentation.transactions.TransactionFormViewModel]
 * (affichage du solde du compte sélectionné dans le formulaire de
 * transaction), pour ne pas le dupliquer une quatrième fois.
 */
fun computeCurrentBalances(accounts: List<Account>, transactions: List<Transaction>): Map<Long, Long> {
    val signedByAccount = transactions
        .groupBy { it.accountId }
        .mapValues { (_, txs) -> txs.sumOf { it.signedAmount() } }
    return accounts.associate { account -> account.id to (account.initialBalance + (signedByAccount[account.id] ?: 0L)) }
}
