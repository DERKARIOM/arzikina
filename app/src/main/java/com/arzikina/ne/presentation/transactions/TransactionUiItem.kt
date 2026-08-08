package com.arzikina.ne.presentation.transactions

import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.Transaction

/**
 * Projection d'affichage combinant une [Transaction] avec le [Account] et la
 * [Category] qu'elle référence. Construite par [TransactionsViewModel] à
 * partir de trois flux Room distincts (pas de dénormalisation en base) :
 * cette classe n'existe que côté presentation.
 *
 * [account]/[category] sont nullables par sécurité (référence orpheline
 * théoriquement impossible grâce aux clés étrangères, voir
 * `data/local/entity/TransactionEntity`), pour que l'UI reste robuste même
 * dans ce cas.
 *
 * @param runningBalance solde du compte juste après cette transaction (voir
 * [computeRunningBalances]), `null` quand il n'est pas calculé pour cet écran
 * (voir [com.arzikina.ne.presentation.dashboard.RecentTransactionsAdapter],
 * qui n'affiche jamais ce champ) ou quand le compte est inconnu.
 */
data class TransactionUiItem(
    val transaction: Transaction,
    val account: Account?,
    val category: Category?,
    val runningBalance: Long? = null
)
