package com.arzikina.ne.domain.model

/**
 * Frais supplémentaires à associer à une [Transaction] (cahier des charges "Gestion des frais
 * supplémentaires sur les transactions") — jamais persistés directement sur la transaction
 * principale : voir `TransactionRepositoryImpl.saveTransaction`, qui les matérialise en une
 * DEUXIÈME [Transaction] normale (type [TransactionType.EXPENSE], catégorie système
 * "Frais et commissions" — voir [FeeCategoryNames]), reliée à la transaction principale via
 * [Transaction.feeTransactionId].
 *
 * @param amount montant des frais en unité mineure de la devise, toujours positif (même
 * convention que [Transaction.amount]).
 * @param accountId compte débité pour les frais — par défaut le même que celui de la transaction
 * principale, mais peut être un compte tiers (cahier des charges, section "Compte utilisé pour
 * les frais").
 * @param type classification des frais (voir [FeeType]).
 * @param description libre, vide par défaut (ex. "Frais de transfert Airtel Money").
 */
data class TransactionFee(
    val amount: Long,
    val accountId: Long,
    val type: FeeType,
    val description: String = ""
)
