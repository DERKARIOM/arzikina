package com.naniger.arzikina.domain.issuedreceipt

/**
 * Erreurs de validation d'un reçu émis. Un code par règle, jamais un texte : la couche
 * présentation les associe aux messages FR/EN (voir l'étape « Formulaire »).
 */
enum class IssuedReceiptError {
    BUSINESS_NAME_BLANK,
    BUSINESS_ADDRESS_BLANK,
    BUSINESS_PHONE_INVALID,
    BUSINESS_EMAIL_INVALID,
    NUMBER_INVALID,
    CUSTOMER_NAME_BLANK,
    CUSTOMER_PHONE_INVALID,
    DESCRIPTION_BLANK,
    AMOUNT_NOT_POSITIVE,
    AMOUNT_TOO_LARGE,
    CURRENCY_INVALID,
    TEXT_TOO_LONG,
    LINES_EMPTY,
    LINE_LABEL_BLANK,
    LINE_QUANTITY_INVALID,
    LINE_PRICE_INVALID,
    DISCOUNT_INVALID,
    TAX_INVALID
}
