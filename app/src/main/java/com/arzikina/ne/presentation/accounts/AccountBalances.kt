package com.arzikina.ne.presentation.accounts

import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.model.signedAmount

/**
 * Solde COURANT de chaque compte (solde initial + somme signée de ses
 * transactions, voir [Transaction.signedAmount]) — calcul partagé par
 * [AccountsViewModel], [AccountDetailViewModel],
 * [com.arzikina.ne.presentation.dashboard.DashboardViewModel] et
 * [com.arzikina.ne.presentation.transactions.TransactionFormViewModel]
 * (affichage du solde du compte sélectionné dans le formulaire de
 * transaction), pour ne pas le dupliquer une cinquième fois.
 *
 * SEUL endroit qui doit connaître les DEUX comptes d'un
 * [TransactionType.TRANSFER] : [Transaction.signedAmount] ne débite que
 * [Transaction.accountId] (le compte source) — cette fonction ajoute en plus
 * le crédit correspondant sur [Transaction.transferAccountId] (le compte
 * destination), pour que le transfert soit neutre au total mais bien réparti
 * entre les deux comptes.
 */
fun computeCurrentBalances(accounts: List<Account>, transactions: List<Transaction>): Map<Long, Long> {
    val deltaByAccount = mutableMapOf<Long, Long>()
    transactions.forEach { transaction ->
        deltaByAccount[transaction.accountId] = (deltaByAccount[transaction.accountId] ?: 0L) + transaction.signedAmount()
        if (transaction.type == TransactionType.TRANSFER) {
            val destinationId = transaction.transferAccountId ?: return@forEach
            deltaByAccount[destinationId] = (deltaByAccount[destinationId] ?: 0L) + transaction.amount
        }
    }
    return accounts.associate { account -> account.id to (account.initialBalance + (deltaByAccount[account.id] ?: 0L)) }
}
