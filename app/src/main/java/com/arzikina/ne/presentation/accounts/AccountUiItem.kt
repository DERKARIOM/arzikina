package com.arzikina.ne.presentation.accounts

import com.arzikina.ne.domain.model.Account

/**
 * [account] enrichi de son solde COURANT (solde initial + somme signée de ses
 * transactions, voir [AccountsViewModel]) — par opposition à
 * [Account.initialBalance], qui reste le solde brut saisi à la création du
 * compte et ne reflète plus les mouvements depuis. Même raisonnement que
 * [com.arzikina.ne.presentation.budget.BudgetUiItem]/
 * [com.arzikina.ne.presentation.transactions.TransactionUiItem] : un modèle
 * de présentation dédié plutôt que de faire porter ce calcul dérivé par le
 * modèle domaine.
 */
data class AccountUiItem(
    val account: Account,
    val currentBalance: Long
)
