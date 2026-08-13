package com.arzikina.ne.domain.model

/**
 * Classification des frais supplémentaires associés à une transaction (voir
 * [Transaction.feeType]) : Transfert / Frais bancaires / Commission / Frais
 * de service / Autre. Uniquement renseigné sur la transaction DE FRAIS
 * auto-générée (jamais sur la transaction principale) — voir
 * [Transaction.feeTransactionId].
 *
 * [OTHER] : le libellé personnalisé que l'utilisateur peut saisir dans ce cas
 * (cahier des charges, section "Types de frais") est porté par
 * [Transaction.description] de la transaction de frais elle-même — pas de
 * champ dédié supplémentaire, ce champ existe déjà sur toute transaction.
 */
enum class FeeType {
    TRANSFER,
    BANK,
    COMMISSION,
    SERVICE,
    OTHER
}
