package com.arzikina.ne.presentation.transactions

import com.arzikina.ne.domain.model.Transaction

/**
 * Ensemble des ids de transactions qui sont elles-mêmes la "transaction de frais" auto-générée
 * et liée à une autre (voir [Transaction.feeTransactionId] et
 * [com.arzikina.ne.data.repository.TransactionRepositoryImpl], architecture "un frais = une
 * transaction de dépense normale liée à sa transaction parente").
 *
 * Une transaction de frais ne doit JAMAIS apparaître comme sa propre ligne dans une liste — elle
 * reste néanmoins une transaction ordinaire pour tout calcul de solde (voir
 * `computeCurrentBalances`/`computeRunningBalances`, qui ne filtrent jamais rien) : ce filtre ne
 * doit donc s'appliquer qu'à la construction des lignes affichées, jamais en amont d'un calcul de
 * solde.
 *
 * Le calcul se fait sur CETTE liste : l'appeler sur un sous-ensemble (ex. transactions d'un seul
 * compte) risquerait de manquer une transaction de frais dont la transaction parente est en
 * dehors de ce sous-ensemble (compte des frais différent du compte source).
 */
fun List<Transaction>.feeTransactionIds(): Set<Long> =
    mapNotNull { it.feeTransactionId }.toSet()
