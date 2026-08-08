package com.arzikina.ne.domain.model

/**
 * Sens d'un flux d'argent : revenu, dépense, ou transfert entre deux comptes.
 *
 * Concept transverse (pas propre aux catégories) : une [Category] est
 * étiquetée avec un [TransactionType] ([INCOME]/[EXPENSE] uniquement — une
 * catégorie "Transfert" n'aurait pas de sens) pour savoir dans quels
 * formulaires la proposer, et chaque transaction porte le même type.
 *
 * [TRANSFER] n'a volontairement PAS de [Category] (voir
 * [com.arzikina.ne.domain.model.Transaction.categoryId], nullable) : un
 * transfert déplace de l'argent entre [com.arzikina.ne.domain.model.Transaction.accountId]
 * et [com.arzikina.ne.domain.model.Transaction.transferAccountId], il ne
 * représente ni un revenu ni une dépense — les écrans Statistiques/Budgets,
 * qui filtrent explicitement sur [INCOME]/[EXPENSE], l'excluent donc déjà de
 * leurs totaux sans traitement particulier.
 */
enum class TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER
}
