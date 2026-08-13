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
 * @param isTransferReceived pour [com.arzikina.ne.domain.model.TransactionType.TRANSFER]
 * uniquement : `true` quand cette ligne représente le point de vue du compte
 * DESTINATION (crédit, [account] == `transaction.transferAccountId`) plutôt
 * que du compte source (débit, comportement par défaut). Toujours `false`
 * pour un revenu/une dépense. Piloté par l'écran qui construit cet item (voir
 * [com.arzikina.ne.presentation.accounts.AccountDetailViewModel] et
 * [TransactionsViewModel]), pas déductible de [transaction] seul puisque
 * celle-ci ne porte pas la notion de "compte consulté".
 * @param feeAmount montant (unités mineures) de la transaction de frais liée (voir
 * [Transaction.feeTransactionId]), `null` si cette transaction n'a pas de frais. Résolu par
 * l'écran qui construit cet item via une recherche dans la liste complète des transactions —
 * jamais stocké sur [Transaction] elle-même (voir [feeTransactionIds]). Sert uniquement à
 * afficher l'indicateur discret "+ Frais X" ([TransactionItemBinder]) ; jamais utilisé pour un
 * calcul de solde (voir `computeCurrentBalances`, qui lit directement la transaction de frais
 * comme n'importe quelle autre transaction).
 */
data class TransactionUiItem(
    val transaction: Transaction,
    val account: Account?,
    val category: Category?,
    val runningBalance: Long? = null,
    val isTransferReceived: Boolean = false,
    val feeAmount: Long? = null
)
