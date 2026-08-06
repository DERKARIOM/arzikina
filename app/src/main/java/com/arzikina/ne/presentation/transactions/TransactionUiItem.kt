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
 */
data class TransactionUiItem(
    val transaction: Transaction,
    val account: Account?,
    val category: Category?
)
