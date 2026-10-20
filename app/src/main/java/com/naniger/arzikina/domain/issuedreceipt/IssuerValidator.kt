package com.naniger.arzikina.domain.issuedreceipt

/**
 * Règles de validation des coordonnées de l'émetteur, PARTAGÉES entre le profil
 * ([com.naniger.arzikina.domain.model.IssuerProfile]) et l'instantané figé dans chaque reçu
 * ([com.naniger.arzikina.domain.model.IssuerSnapshot]) : une seule définition, jamais dupliquée.
 */
object IssuerValidator {

    const val MAX_NAME_LENGTH = 120
    const val MAX_ADDRESS_LENGTH = 250
    const val MAX_SHORT_TEXT_LENGTH = 120

    private val phone = Regex("^[0-9+()\\-. ]{5,30}$")
    private val email = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    /** Téléphone : chiffres, `+`, espaces, parenthèses, tirets et points ; 5 à 30 caractères. */
    fun isValidPhone(value: String): Boolean = phone.matches(value.trim())

    fun validate(
        businessName: String,
        address: String,
        phone: String,
        email: String?,
        taxId: String?,
        issuedByName: String? = null,
        issuedByTitle: String? = null
    ): Set<IssuedReceiptError> {
        val errors = linkedSetOf<IssuedReceiptError>()
        if (businessName.isBlank()) errors += IssuedReceiptError.BUSINESS_NAME_BLANK
        if (address.isBlank()) errors += IssuedReceiptError.BUSINESS_ADDRESS_BLANK
        if (!isValidPhone(phone)) errors += IssuedReceiptError.BUSINESS_PHONE_INVALID
        email?.takeIf { it.isNotBlank() }?.let {
            if (!this.email.matches(it.trim())) errors += IssuedReceiptError.BUSINESS_EMAIL_INVALID
        }
        val tooLong = businessName.length > MAX_NAME_LENGTH ||
            address.length > MAX_ADDRESS_LENGTH ||
            (taxId?.length ?: 0) > MAX_SHORT_TEXT_LENGTH ||
            (issuedByName?.length ?: 0) > MAX_SHORT_TEXT_LENGTH ||
            (issuedByTitle?.length ?: 0) > MAX_SHORT_TEXT_LENGTH
        if (tooLong) errors += IssuedReceiptError.TEXT_TOO_LONG
        return errors
    }
}
