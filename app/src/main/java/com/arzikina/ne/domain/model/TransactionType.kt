package com.arzikina.ne.domain.model

/**
 * Sens d'un flux d'argent : revenu ou dépense.
 *
 * Concept transverse (pas propre aux catégories) : une [Category] est
 * étiquetée avec un [TransactionType] pour savoir dans quels formulaires la
 * proposer, et chaque transaction (à venir) portera le même type.
 */
enum class TransactionType {
    INCOME,
    EXPENSE
}
