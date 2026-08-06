package com.arzikina.ne.domain.model

/**
 * Compte financier de l'utilisateur (Espèces, Banque, Mobile Money, Épargne,
 * Wallet, ou tout autre compte personnalisé).
 *
 * [initialBalance] est exprimé dans l'unité mineure de la devise (ex. les
 * centimes pour l'EUR) et non en [Double], pour éliminer tout risque
 * d'erreur d'arrondi propre aux nombres à virgule flottante. Cette règle
 * s'appliquera à tous les montants de l'application (transactions, budgets,
 * objectifs d'épargne...).
 *
 * @param id 0L tant que le compte n'a pas encore été enregistré en base.
 */
data class Account(
    val id: Long = 0L,
    val name: String,
    val icon: AccountIcon,
    val colorArgb: Long,
    val currencyCode: String,
    val initialBalance: Long,
    val createdAt: Long
)
