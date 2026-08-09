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
/**
 * @param cardHolderName nom affiché sur une carte [com.arzikina.ne.domain.model.AccountType.CREDIT_CARD]
 * (voir [AccountCardCreditBinder]) — le nom de l'utilisateur connecté, pas un
 * champ propre au compte (même choix que la carte VISA du Dashboard). Sans
 * effet pour les autres types de compte. Porté ici plutôt que par
 * [AccountsAdapter] : `ListAdapter`/`DiffUtil` ne suivent que le contenu de
 * la liste soumise, un champ externe à l'item ne déclencherait pas de
 * rafraîchissement si l'utilisateur renommait son profil.
 */
data class AccountUiItem(
    val account: Account,
    val currentBalance: Long,
    val cardHolderName: String = ""
)
