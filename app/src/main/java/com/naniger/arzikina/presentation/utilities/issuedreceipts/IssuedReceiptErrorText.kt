package com.naniger.arzikina.presentation.utilities.issuedreceipts

import androidx.annotation.StringRes
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.issuedreceipt.IssuedReceiptError

/** Seul endroit qui associe un code d'erreur de validation à son message FR/EN. */
@StringRes
fun IssuedReceiptError.messageRes(): Int = when (this) {
    IssuedReceiptError.BUSINESS_NAME_BLANK -> R.string.issued_receipt_error_business_name
    IssuedReceiptError.BUSINESS_ADDRESS_BLANK -> R.string.issued_receipt_error_business_address
    IssuedReceiptError.BUSINESS_PHONE_INVALID -> R.string.issued_receipt_error_phone
    IssuedReceiptError.BUSINESS_EMAIL_INVALID -> R.string.issued_receipt_error_email
    IssuedReceiptError.NUMBER_INVALID -> R.string.issued_receipt_error_number_invalid
    IssuedReceiptError.CUSTOMER_NAME_BLANK -> R.string.issued_receipt_error_customer_name
    IssuedReceiptError.CUSTOMER_PHONE_INVALID -> R.string.issued_receipt_error_phone
    IssuedReceiptError.DESCRIPTION_BLANK -> R.string.issued_receipt_error_description
    IssuedReceiptError.AMOUNT_NOT_POSITIVE -> R.string.issued_receipt_error_amount
    IssuedReceiptError.AMOUNT_TOO_LARGE -> R.string.issued_receipt_error_amount_too_large
    IssuedReceiptError.CURRENCY_INVALID -> R.string.issued_receipt_error_currency
    IssuedReceiptError.TEXT_TOO_LONG -> R.string.issued_receipt_error_text_too_long
    IssuedReceiptError.LINES_EMPTY,
    IssuedReceiptError.LINE_LABEL_BLANK,
    IssuedReceiptError.LINE_QUANTITY_INVALID,
    IssuedReceiptError.LINE_PRICE_INVALID,
    IssuedReceiptError.DISCOUNT_INVALID,
    IssuedReceiptError.TAX_INVALID -> R.string.issued_receipt_error_pricing
}
