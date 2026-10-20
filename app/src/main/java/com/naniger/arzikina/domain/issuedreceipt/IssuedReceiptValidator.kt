package com.naniger.arzikina.domain.issuedreceipt

import com.naniger.arzikina.domain.model.IssuedReceipt
import com.naniger.arzikina.domain.model.ReceiptDiscount

/**
 * Règles de validation d'un [IssuedReceipt] AVANT enregistrement ou génération du PDF. Logique
 * pure, testée unitairement ; le repository la rappelle toujours (la couche présentation ne
 * contourne jamais la validation).
 *
 * Retourne l'ensemble des erreurs (vide = valide) pour que le formulaire puisse toutes les
 * afficher d'un coup plutôt qu'une par une.
 */
object IssuedReceiptValidator {

    const val MAX_NAME_LENGTH = 120
    const val MAX_ADDRESS_LENGTH = 250
    const val MAX_DESCRIPTION_LENGTH = 2_000
    const val MAX_NOTES_LENGTH = 1_000
    const val MAX_SHORT_TEXT_LENGTH = 120
    const val MAX_LINES = 100

    private val phone = Regex("^[0-9+()\\-. ]{5,30}$")
    private val email = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val currency = Regex("^[A-Z]{3}$")

    /** [receipt].number vide = « à générer » : valide à ce stade. */
    fun validate(receipt: IssuedReceipt): Set<IssuedReceiptError> {
        val errors = linkedSetOf<IssuedReceiptError>()

        val issuer = receipt.issuer
        if (issuer.businessName.isBlank()) errors += IssuedReceiptError.BUSINESS_NAME_BLANK
        if (issuer.address.isBlank()) errors += IssuedReceiptError.BUSINESS_ADDRESS_BLANK
        if (!phone.matches(issuer.phone.trim())) errors += IssuedReceiptError.BUSINESS_PHONE_INVALID
        issuer.email?.takeIf { it.isNotBlank() }?.let {
            if (!email.matches(it.trim())) errors += IssuedReceiptError.BUSINESS_EMAIL_INVALID
        }

        if (receipt.number.isNotEmpty() && !ReceiptNumber.isValid(receipt.number)) {
            errors += IssuedReceiptError.NUMBER_INVALID
        }
        if (receipt.customerName.isBlank()) errors += IssuedReceiptError.CUSTOMER_NAME_BLANK
        receipt.customerPhone?.takeIf { it.isNotBlank() }?.let {
            if (!phone.matches(it.trim())) errors += IssuedReceiptError.CUSTOMER_PHONE_INVALID
        }
        if (receipt.description.isBlank()) errors += IssuedReceiptError.DESCRIPTION_BLANK
        if (!currency.matches(receipt.currencyCode)) errors += IssuedReceiptError.CURRENCY_INVALID

        val tooLong = receipt.customerName.length > MAX_NAME_LENGTH ||
            issuer.businessName.length > MAX_NAME_LENGTH ||
            issuer.address.length > MAX_ADDRESS_LENGTH ||
            receipt.description.length > MAX_DESCRIPTION_LENGTH ||
            (receipt.notes?.length ?: 0) > MAX_NOTES_LENGTH ||
            (receipt.transactionReference?.length ?: 0) > MAX_SHORT_TEXT_LENGTH ||
            (receipt.issuedByName?.length ?: 0) > MAX_SHORT_TEXT_LENGTH ||
            (receipt.issuedByTitle?.length ?: 0) > MAX_SHORT_TEXT_LENGTH ||
            (issuer.taxId?.length ?: 0) > MAX_SHORT_TEXT_LENGTH
        if (tooLong) errors += IssuedReceiptError.TEXT_TOO_LONG

        val pricing = receipt.pricing
        if (pricing == null) {
            when {
                receipt.amountMinor <= 0 -> errors += IssuedReceiptError.AMOUNT_NOT_POSITIVE
                receipt.amountMinor > ReceiptCalculator.MAX_AMOUNT_MINOR -> errors += IssuedReceiptError.AMOUNT_TOO_LARGE
            }
        } else {
            validatePricing(receipt, errors)
        }
        return errors
    }

    private fun validatePricing(receipt: IssuedReceipt, errors: MutableSet<IssuedReceiptError>) {
        val pricing = checkNotNull(receipt.pricing)
        if (pricing.lines.isEmpty() || pricing.lines.size > MAX_LINES) errors += IssuedReceiptError.LINES_EMPTY
        pricing.lines.forEach { line ->
            if (line.label.isBlank()) errors += IssuedReceiptError.LINE_LABEL_BLANK
            if (line.label.length > MAX_NAME_LENGTH) errors += IssuedReceiptError.TEXT_TOO_LONG
            if (line.quantityMilli <= 0) errors += IssuedReceiptError.LINE_QUANTITY_INVALID
            if (line.unitPriceMinor < 0) errors += IssuedReceiptError.LINE_PRICE_INVALID
        }
        if (pricing.taxBasisPoints !in 0..ReceiptCalculator.MAX_TAX_BASIS_POINTS) errors += IssuedReceiptError.TAX_INVALID
        val discountInvalid = when (val d = pricing.discount) {
            ReceiptDiscount.None -> false
            is ReceiptDiscount.Percent -> d.basisPoints !in 0..ReceiptCalculator.MAX_TAX_BASIS_POINTS
            is ReceiptDiscount.Fixed -> d.amountMinor < 0
        }
        if (discountInvalid) errors += IssuedReceiptError.DISCOUNT_INVALID

        // Les erreurs de lignes ci-dessus expliquent déjà un calcul impossible ; on ne signale
        // « trop grand » / « non positif » que si elles sont absentes.
        if (errors.none { it in structuralPricingErrors }) {
            val totals = ReceiptCalculator.totalsOrNull(pricing)
            when {
                totals == null -> errors += IssuedReceiptError.AMOUNT_TOO_LARGE
                totals.totalMinor <= 0 -> errors += IssuedReceiptError.AMOUNT_NOT_POSITIVE
            }
        }
    }

    private val structuralPricingErrors = setOf(
        IssuedReceiptError.LINES_EMPTY,
        IssuedReceiptError.LINE_LABEL_BLANK,
        IssuedReceiptError.LINE_QUANTITY_INVALID,
        IssuedReceiptError.LINE_PRICE_INVALID,
        IssuedReceiptError.DISCOUNT_INVALID,
        IssuedReceiptError.TAX_INVALID
    )
}
