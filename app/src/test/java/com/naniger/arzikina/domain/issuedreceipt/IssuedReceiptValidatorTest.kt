package com.naniger.arzikina.domain.issuedreceipt

import com.naniger.arzikina.domain.model.IssuedReceipt
import com.naniger.arzikina.domain.model.IssuerSnapshot
import com.naniger.arzikina.domain.model.PaymentMethod
import com.naniger.arzikina.domain.model.ReceiptDiscount
import com.naniger.arzikina.domain.model.ReceiptLine
import com.naniger.arzikina.domain.model.ReceiptPricing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IssuedReceiptValidatorTest {

    private val issuer = IssuerSnapshot(businessName = "Boutique Aïssa", address = "Niamey, Plateau", phone = "+227 90 00 00 00")

    private fun valid() = IssuedReceipt(
        issuedAt = 1_000L,
        customerName = "Boubé",
        description = "Achat de tissu",
        amountMinor = 500_000L,
        currencyCode = "XOF",
        paymentMethod = PaymentMethod.CASH,
        issuer = issuer
    )

    @Test
    fun `a complete simple receipt is valid`() {
        assertTrue(IssuedReceiptValidator.validate(valid()).isEmpty())
    }

    @Test
    fun `blank required fields are reported together`() {
        val errors = IssuedReceiptValidator.validate(
            valid().copy(customerName = " ", description = "", issuer = issuer.copy(businessName = "", address = ""))
        )
        assertEquals(
            setOf(
                IssuedReceiptError.CUSTOMER_NAME_BLANK,
                IssuedReceiptError.DESCRIPTION_BLANK,
                IssuedReceiptError.BUSINESS_NAME_BLANK,
                IssuedReceiptError.BUSINESS_ADDRESS_BLANK
            ),
            errors
        )
    }

    @Test
    fun `amount must be positive and bounded for a simple receipt`() {
        assertEquals(setOf(IssuedReceiptError.AMOUNT_NOT_POSITIVE), IssuedReceiptValidator.validate(valid().copy(amountMinor = 0)))
        assertEquals(
            setOf(IssuedReceiptError.AMOUNT_TOO_LARGE),
            IssuedReceiptValidator.validate(valid().copy(amountMinor = ReceiptCalculator.MAX_AMOUNT_MINOR + 1))
        )
    }

    @Test
    fun `phone email currency and number formats are checked`() {
        val errors = IssuedReceiptValidator.validate(
            valid().copy(
                customerPhone = "abc",
                currencyCode = "xof",
                number = "bad number",
                issuer = issuer.copy(phone = "12", email = "not-an-email")
            )
        )
        assertEquals(
            setOf(
                IssuedReceiptError.CUSTOMER_PHONE_INVALID,
                IssuedReceiptError.CURRENCY_INVALID,
                IssuedReceiptError.NUMBER_INVALID,
                IssuedReceiptError.BUSINESS_PHONE_INVALID,
                IssuedReceiptError.BUSINESS_EMAIL_INVALID
            ),
            errors
        )
    }

    @Test
    fun `empty optional fields are accepted`() {
        val errors = IssuedReceiptValidator.validate(valid().copy(customerPhone = "", issuer = issuer.copy(email = "")))
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `too long text is rejected`() {
        val errors = IssuedReceiptValidator.validate(valid().copy(description = "x".repeat(IssuedReceiptValidator.MAX_DESCRIPTION_LENGTH + 1)))
        assertEquals(setOf(IssuedReceiptError.TEXT_TOO_LONG), errors)
    }

    @Test
    fun `detailed receipt needs at least one valid line`() {
        val empty = valid().copy(pricing = ReceiptPricing(emptyList()))
        assertEquals(setOf(IssuedReceiptError.LINES_EMPTY), IssuedReceiptValidator.validate(empty))

        val badLine = valid().copy(pricing = ReceiptPricing(listOf(ReceiptLine(" ", 0, -5))))
        assertEquals(
            setOf(
                IssuedReceiptError.LINE_LABEL_BLANK,
                IssuedReceiptError.LINE_QUANTITY_INVALID,
                IssuedReceiptError.LINE_PRICE_INVALID
            ),
            IssuedReceiptValidator.validate(badLine)
        )
    }

    @Test
    fun `detailed receipt validates discount tax and resulting total`() {
        val line = ReceiptLine("Tissu", 1_000, 10_000)
        assertTrue(IssuedReceiptValidator.validate(valid().copy(amountMinor = 0, pricing = ReceiptPricing(listOf(line)))).isEmpty())

        assertEquals(
            setOf(IssuedReceiptError.TAX_INVALID),
            IssuedReceiptValidator.validate(valid().copy(pricing = ReceiptPricing(listOf(line), taxBasisPoints = 20_000)))
        )
        assertEquals(
            setOf(IssuedReceiptError.DISCOUNT_INVALID),
            IssuedReceiptValidator.validate(valid().copy(pricing = ReceiptPricing(listOf(line), discount = ReceiptDiscount.Percent(-1))))
        )
        // Remise totale : le total vaut 0 → refusé.
        assertEquals(
            setOf(IssuedReceiptError.AMOUNT_NOT_POSITIVE),
            IssuedReceiptValidator.validate(valid().copy(pricing = ReceiptPricing(listOf(line), discount = ReceiptDiscount.Fixed(10_000))))
        )
    }
}
