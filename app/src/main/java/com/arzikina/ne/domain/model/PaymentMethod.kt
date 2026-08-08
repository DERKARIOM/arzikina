package com.arzikina.ne.domain.model

/**
 * Moyen de paiement d'une transaction, optionnel ("si applicable" — voir
 * [Transaction.paymentMethod]) : contrairement à [Account]/[Category], une
 * transaction n'a pas TOUJOURS un moyen de paiement identifiable (ex. import
 * futur depuis un relevé, ou l'utilisateur ne souhaite simplement pas le
 * préciser). Aucun texte affichable ici, comme [SecurityQuestion] : voir
 * [com.arzikina.ne.presentation.transactions.PaymentMethodDisplay].
 *
 * [MOBILE_MONEY] distinct de [CARD]/[BANK_TRANSFER] : moyen de paiement
 * central dans le contexte africain visé par l'app (voir instructions
 * projet), pas une simple variante de virement.
 */
enum class PaymentMethod {
    CASH,
    CARD,
    MOBILE_MONEY,
    BANK_TRANSFER,
    OTHER
}
